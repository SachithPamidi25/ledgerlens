package com.ledgerlens.insights;

import java.time.Instant;
import java.util.List;

public record SpendingQuestionResponse(
        String question,
        String answer,
        List<ReceiptSearchResult> sources,
        String generatedAt
) {
    public static SpendingQuestionResponse empty(String question, String answer) {
        return new SpendingQuestionResponse(question, answer, List.of(), Instant.now().toString());
    }
}
