package com.ledgerlens.agent;

import java.time.Instant;
import java.util.Map;

public record FinanceAgentResponse(
        String question,
        String toolName,
        String answer,
        Map<String, Object> result,
        String generatedAt
) {
    public static FinanceAgentResponse readOnlyRefusal(String question) {
        return new FinanceAgentResponse(
                question,
                null,
                "I can only analyze receipts and ledger data with read-only tools. I cannot delete, modify, create, or reclassify financial records.",
                Map.of(),
                Instant.now().toString()
        );
    }
}
