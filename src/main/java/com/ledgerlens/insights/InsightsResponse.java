package com.ledgerlens.insights;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record InsightsResponse(
        String period,
        BigDecimal totalSpent,
        int receiptCount,
        Map<String, BigDecimal> byCategory,
        List<Insight> insights,
        String generatedAt
) {}
