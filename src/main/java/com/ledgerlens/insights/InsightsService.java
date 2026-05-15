package com.ledgerlens.insights;

import com.ledgerlens.receipt.Receipt;
import com.ledgerlens.receipt.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final ReceiptRepository receiptRepository;
    private final ClaudeInsightsService claudeInsightsService;

    @Transactional(readOnly = true)
    public InsightsResponse generateInsights(UUID userId, int months) {
        LocalDate fromDate = LocalDate.now().minusMonths(months).withDayOfMonth(1);
        List<Receipt> receipts = receiptRepository.findCompletedSince(userId, fromDate);

        if (receipts.isEmpty()) {
            return new InsightsResponse(
                    buildPeriodLabel(fromDate),
                    BigDecimal.ZERO,
                    0,
                    Map.of(),
                    List.of(new Insight("INFO", "No data yet",
                            "Upload some receipts to get AI-powered spending insights.")),
                    Instant.now().toString()
            );
        }

        BigDecimal totalSpent = receipts.stream()
                .map(r -> r.getTotal() != null ? r.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = receipts.stream()
                .filter(r -> r.getMerchantCategory() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getMerchantCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> r.getTotal() != null ? r.getTotal() : BigDecimal.ZERO,
                                BigDecimal::add)));

        Map<String, BigDecimal> byMerchant = receipts.stream()
                .filter(r -> r.getVendor() != null)
                .collect(Collectors.groupingBy(
                        Receipt::getVendor,
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> r.getTotal() != null ? r.getTotal() : BigDecimal.ZERO,
                                BigDecimal::add)));

        Map<String, Long> merchantCounts = receipts.stream()
                .filter(r -> r.getVendor() != null)
                .collect(Collectors.groupingBy(Receipt::getVendor, Collectors.counting()));

        Map<String, BigDecimal> byMonth = receipts.stream()
                .filter(r -> r.getReceiptDate() != null)
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getReceiptDate()).toString(),
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> r.getTotal() != null ? r.getTotal() : BigDecimal.ZERO,
                                BigDecimal::add)));

        Map<String, Long> receiptsByMonth = receipts.stream()
                .filter(r -> r.getReceiptDate() != null)
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getReceiptDate()).toString(),
                        TreeMap::new,
                        Collectors.counting()));

        String prompt = buildPrompt(months, fromDate, totalSpent, receipts.size(),
                byCategory, byMerchant, merchantCounts, byMonth, receiptsByMonth);

        log.info("Requesting AI insights for user {} ({} receipts, {} months)", userId, receipts.size(), months);
        List<Insight> insights = claudeInsightsService.generateInsights(prompt);

        return new InsightsResponse(
                buildPeriodLabel(fromDate),
                totalSpent.setScale(2, RoundingMode.HALF_UP),
                receipts.size(),
                byCategory,
                insights,
                Instant.now().toString()
        );
    }

    private String buildPrompt(int months, LocalDate fromDate, BigDecimal totalSpent,
                               int receiptCount, Map<String, BigDecimal> byCategory,
                               Map<String, BigDecimal> byMerchant, Map<String, Long> merchantCounts,
                               Map<String, BigDecimal> byMonth, Map<String, Long> receiptsByMonth) {

        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a personal finance advisor. Analyze the spending data below and return ONLY a valid JSON array.
                No explanation, no markdown, only the raw JSON array.

                """);

        sb.append("Spending data for the last ").append(months).append(" month(s):\n");
        sb.append("Period: ").append(fromDate).append(" to ").append(LocalDate.now()).append("\n");
        sb.append("Total spent: ").append(totalSpent.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Total receipts: ").append(receiptCount).append("\n\n");

        sb.append("Spending by category:\n");
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> {
                    BigDecimal pct = totalSpent.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().multiply(BigDecimal.valueOf(100))
                                    .divide(totalSpent, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    sb.append("  ").append(e.getKey()).append(": ")
                      .append(e.getValue().setScale(2, RoundingMode.HALF_UP))
                      .append(" (").append(pct).append("%)\n");
                });

        sb.append("\nMonthly breakdown:\n");
        byMonth.forEach((month, amount) -> {
            long count = receiptsByMonth.getOrDefault(month, 0L);
            sb.append("  ").append(month).append(": ")
              .append(amount.setScale(2, RoundingMode.HALF_UP))
              .append(" (").append(count).append(" receipt").append(count == 1 ? "" : "s").append(")\n");
        });

        Map<String, BigDecimal> topMerchants = byMerchant.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        if (!topMerchants.isEmpty()) {
            sb.append("\nTop merchants by spend:\n");
            topMerchants.forEach((merchant, amount) -> {
                long count = merchantCounts.getOrDefault(merchant, 0L);
                sb.append("  ").append(merchant).append(": ")
                  .append(amount.setScale(2, RoundingMode.HALF_UP))
                  .append(" (").append(count).append(" purchase").append(count == 1 ? "" : "s").append(")\n");
            });
        }

        sb.append("""

                Return a JSON array of 3 to 5 insights. Each must have exactly:
                [
                  {
                    "type": "one of: TREND, TOP_SPEND, RECOMMENDATION, ANOMALY",
                    "title": "short title under 60 characters",
                    "detail": "1-2 sentences with specific numbers from the data above"
                  }
                ]
                """);

        return sb.toString();
    }

    private String buildPeriodLabel(LocalDate fromDate) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        return fromDate.format(fmt) + " – " + LocalDate.now().format(fmt);
    }
}
