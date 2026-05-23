package com.ledgerlens.insights;

import com.ledgerlens.receipt.MerchantCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroundedSpendingQaService {

    private final ReceiptSemanticIndexService receiptSemanticIndexService;

    @Transactional
    public SpendingQuestionResponse answerQuestion(UUID userId, String question, int limit) {
        if (question == null || question.isBlank()) {
            return SpendingQuestionResponse.empty("", "Ask a spending question to search your receipts.");
        }

        int boundedLimit = Math.max(1, Math.min(limit, 10));
        List<ReceiptSearchResult> sources = receiptSemanticIndexService.search(userId, question, boundedLimit);
        if (sources.isEmpty()) {
            return SpendingQuestionResponse.empty(
                    question,
                    "I could not find completed receipts that support an answer to that question yet."
            );
        }

        String answer = buildGroundedAnswer(question, sources);
        receiptSemanticIndexService.recordInsightSources(userId, question, answer, sources);

        return new SpendingQuestionResponse(question, answer, sources, Instant.now().toString());
    }

    private String buildGroundedAnswer(String question, List<ReceiptSearchResult> sources) {
        BigDecimal total = sources.stream()
                .map(source -> source.total() == null ? BigDecimal.ZERO : source.total())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String currency = sources.stream()
                .map(ReceiptSearchResult::currency)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("INR");

        Map<MerchantCategory, BigDecimal> byCategory = sources.stream()
                .filter(source -> source.category() != null)
                .collect(Collectors.groupingBy(
                        ReceiptSearchResult::category,
                        Collectors.reducing(BigDecimal.ZERO,
                                source -> source.total() == null ? BigDecimal.ZERO : source.total(),
                                BigDecimal::add)));

        String topCategory = byCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().name() + " at " + money(entry.getValue(), currency))
                .orElse("uncategorized spend");

        String evidence = sources.stream()
                .sorted(Comparator.comparing(ReceiptSearchResult::distance))
                .limit(3)
                .map(this::formatEvidence)
                .collect(Collectors.joining("; "));

        String prefix = question.toLowerCase().contains("why")
                ? "The strongest receipt evidence points to "
                : "Based on the closest matching receipts, ";

        return prefix + topCategory + " across " + sources.size() + " receipt"
                + (sources.size() == 1 ? "" : "s")
                + ", totaling " + money(total, currency) + ". Evidence: " + evidence + ".";
    }

    private String formatEvidence(ReceiptSearchResult source) {
        String date = source.receiptDate() == null
                ? "unknown date"
                : source.receiptDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String merchant = source.vendor() == null ? "Unknown merchant" : source.vendor();
        String category = source.category() == null ? "OTHER" : source.category().name();
        String currency = source.currency() == null ? "INR" : source.currency();
        BigDecimal total = source.total() == null ? BigDecimal.ZERO : source.total();
        return merchant + " on " + date + " (" + category + ", " + money(total, currency) + ")";
    }

    private String money(BigDecimal amount, String currency) {
        return currency + " " + amount.setScale(2, RoundingMode.HALF_UP);
    }
}
