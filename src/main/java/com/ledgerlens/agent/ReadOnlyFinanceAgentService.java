package com.ledgerlens.agent;

import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.Receipt;
import com.ledgerlens.receipt.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadOnlyFinanceAgentService {

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(20\\d{2}|19\\d{2})[-/](0?[1-9]|1[0-2])");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:over|above|greater than|threshold)?\\s*(\\d+(?:\\.\\d{1,2})?)");

    private final ReceiptRepository receiptRepository;
    private final AiToolCallAuditService auditService;

    @Transactional(readOnly = true)
    public FinanceAgentResponse answer(UUID userId, String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return new FinanceAgentResponse("", null, "Ask a finance question to run a read-only tool.", Map.of(), now());
        }
        if (asksForForbiddenMutation(normalized)) {
            return FinanceAgentResponse.readOnlyRefusal(question);
        }

        if (normalized.contains("duplicate")) {
            return callTool(userId, "findDuplicateReceipts", Map.of(), () -> findDuplicateReceipts(userId), question);
        }
        if (normalized.contains("high value") || normalized.contains("large transaction") || normalized.contains("expensive")) {
            BigDecimal threshold = parseThreshold(normalized);
            return callTool(userId, "listHighValueTransactions", Map.of("threshold", threshold), () ->
                    listHighValueTransactions(userId, threshold), question);
        }
        if (normalized.contains("spike") || normalized.contains("increase") || normalized.contains("increased")) {
            YearMonth month = parseMonth(normalized);
            MerchantCategory category = parseCategory(normalized);
            return callTool(userId, "explainCategorySpike", Map.of("category", category.name(), "month", month.toString()), () ->
                    explainCategorySpike(userId, category, month), question);
        }
        if (normalized.contains("category") || normalized.contains("breakdown")) {
            YearMonth month = parseMonth(normalized);
            return callTool(userId, "getCategoryBreakdown", Map.of("month", month.toString()), () ->
                    getCategoryBreakdown(userId, month), question);
        }

        YearMonth month = parseMonth(normalized);
        return callTool(userId, "getMonthlySpend", Map.of("month", month.toString()), () ->
                getMonthlySpend(userId, month), question);
    }

    private FinanceAgentResponse callTool(
            UUID userId,
            String toolName,
            Map<String, Object> arguments,
            ToolInvocation invocation,
            String question
    ) {
        long start = System.nanoTime();
        try {
            Map<String, Object> result = invocation.run();
            long latencyMs = elapsedMs(start);
            auditService.record(userId, toolName, arguments, "SUCCESS", latencyMs);
            return new FinanceAgentResponse(question, toolName, answerFor(toolName, result), result, now());
        } catch (RuntimeException e) {
            long latencyMs = elapsedMs(start);
            auditService.record(userId, toolName, arguments, "FAILED", latencyMs);
            throw e;
        }
    }

    private Map<String, Object> getMonthlySpend(UUID userId, YearMonth month) {
        List<Receipt> receipts = receiptsForMonth(userId, month);
        BigDecimal total = total(receipts);
        return Map.of(
                "month", month.toString(),
                "totalSpend", total,
                "receiptCount", receipts.size()
        );
    }

    private Map<String, Object> getCategoryBreakdown(UUID userId, YearMonth month) {
        List<Receipt> receipts = receiptsForMonth(userId, month);
        Map<String, BigDecimal> byCategory = receipts.stream()
                .filter(receipt -> receipt.getMerchantCategory() != null)
                .collect(Collectors.groupingBy(
                        receipt -> receipt.getMerchantCategory().name(),
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO,
                                receipt -> receipt.getTotal() == null ? BigDecimal.ZERO : receipt.getTotal(),
                                BigDecimal::add)));
        return Map.of("month", month.toString(), "byCategory", byCategory, "receiptCount", receipts.size());
    }

    private Map<String, Object> findDuplicateReceipts(UUID userId) {
        List<Map<String, Object>> duplicates = receiptRepository.findAllByUserId(userId).stream()
                .filter(receipt -> receipt.getContentHash() != null && !receipt.getContentHash().isBlank())
                .collect(Collectors.groupingBy(Receipt::getContentHash))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> Map.<String, Object>of(
                        "contentHash", entry.getKey(),
                        "receiptCount", entry.getValue().size(),
                        "receiptIds", entry.getValue().stream().map(Receipt::getId).toList()
                ))
                .toList();
        return Map.of("duplicateGroups", duplicates, "duplicateGroupCount", duplicates.size());
    }

    private Map<String, Object> explainCategorySpike(UUID userId, MerchantCategory category, YearMonth month) {
        YearMonth previousMonth = month.minusMonths(1);
        List<Receipt> currentReceipts = receiptsForMonth(userId, month).stream()
                .filter(receipt -> receipt.getMerchantCategory() == category)
                .toList();
        List<Receipt> previousReceipts = receiptsForMonth(userId, previousMonth).stream()
                .filter(receipt -> receipt.getMerchantCategory() == category)
                .toList();

        BigDecimal currentTotal = total(currentReceipts);
        BigDecimal previousTotal = total(previousReceipts);
        BigDecimal change = currentTotal.subtract(previousTotal).setScale(2, RoundingMode.HALF_UP);

        List<Map<String, Object>> topReceipts = currentReceipts.stream()
                .sorted(Comparator.comparing((Receipt receipt) -> receipt.getTotal() == null ? BigDecimal.ZERO : receipt.getTotal()).reversed())
                .limit(5)
                .map(this::receiptSummary)
                .toList();

        return Map.of(
                "category", category.name(),
                "month", month.toString(),
                "previousMonth", previousMonth.toString(),
                "currentTotal", currentTotal,
                "previousTotal", previousTotal,
                "change", change,
                "topReceipts", topReceipts
        );
    }

    private Map<String, Object> listHighValueTransactions(UUID userId, BigDecimal threshold) {
        List<Map<String, Object>> receipts = receiptRepository.findAllByUserId(userId).stream()
                .filter(receipt -> receipt.getTotal() != null && receipt.getTotal().compareTo(threshold) >= 0)
                .sorted(Comparator.comparing(Receipt::getTotal).reversed())
                .limit(20)
                .map(this::receiptSummary)
                .toList();
        return Map.of("threshold", threshold, "transactions", receipts, "count", receipts.size());
    }

    private List<Receipt> receiptsForMonth(UUID userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        return receiptRepository.findCompletedForSummaryBetween(userId, from, to);
    }

    private Map<String, Object> receiptSummary(Receipt receipt) {
        return Map.of(
                "receiptId", receipt.getId(),
                "merchant", receipt.getVendor() == null ? "Unknown merchant" : receipt.getVendor(),
                "category", receipt.getMerchantCategory() == null ? MerchantCategory.OTHER.name() : receipt.getMerchantCategory().name(),
                "date", receipt.getReceiptDate() == null ? "" : receipt.getReceiptDate().toString(),
                "total", receipt.getTotal() == null ? BigDecimal.ZERO : receipt.getTotal(),
                "currency", receipt.getCurrency() == null ? "INR" : receipt.getCurrency()
        );
    }

    private String answerFor(String toolName, Map<String, Object> result) {
        return switch (toolName) {
            case "getMonthlySpend" -> "Monthly spend was " + result.get("totalSpend")
                    + " across " + result.get("receiptCount") + " completed receipts.";
            case "getCategoryBreakdown" -> "Category breakdown is ready from completed receipts for " + result.get("month") + ".";
            case "findDuplicateReceipts" -> "Found " + result.get("duplicateGroupCount") + " duplicate receipt group(s).";
            case "explainCategorySpike" -> "Category change was " + result.get("change")
                    + " compared with " + result.get("previousMonth") + ".";
            case "listHighValueTransactions" -> "Found " + result.get("count")
                    + " transaction(s) at or above " + result.get("threshold") + ".";
            default -> "Read-only finance tool completed.";
        };
    }

    private boolean asksForForbiddenMutation(String normalized) {
        return normalized.contains("delete")
                || normalized.contains("modify")
                || normalized.contains("change receipt")
                || normalized.contains("change amount")
                || normalized.contains("create expense")
                || normalized.contains("without approval")
                || normalized.contains("reclassify");
    }

    private YearMonth parseMonth(String normalized) {
        Matcher matcher = YEAR_MONTH_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }
        for (Month month : Month.values()) {
            if (normalized.contains(month.name().toLowerCase(Locale.ROOT))) {
                return YearMonth.of(LocalDate.now().getYear(), month);
            }
        }
        return YearMonth.now();
    }

    private MerchantCategory parseCategory(String normalized) {
        for (MerchantCategory category : MerchantCategory.values()) {
            if (normalized.contains(category.name().toLowerCase(Locale.ROOT))) {
                return category;
            }
        }
        return MerchantCategory.FOOD;
    }

    private BigDecimal parseThreshold(String normalized) {
        Matcher matcher = AMOUNT_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return new BigDecimal("100.00");
    }

    private BigDecimal total(List<Receipt> receipts) {
        return receipts.stream()
                .map(receipt -> receipt.getTotal() == null ? BigDecimal.ZERO : receipt.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private String now() {
        return java.time.Instant.now().toString();
    }

    @FunctionalInterface
    private interface ToolInvocation {
        Map<String, Object> run();
    }
}
