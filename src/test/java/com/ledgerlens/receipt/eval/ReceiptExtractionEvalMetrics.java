package com.ledgerlens.receipt.eval;

import java.math.BigDecimal;
import java.math.RoundingMode;

record ReceiptExtractionEvalMetrics(
        int datasetSize,
        int validJsonCount,
        int validationFailureCount,
        int merchantMatches,
        int dateMatches,
        int amountMatches,
        int categoryMatches,
        long totalLatencyMs,
        BigDecimal totalEstimatedCostUsd
) {
    BigDecimal merchantAccuracy() {
        return percentage(merchantMatches);
    }

    BigDecimal dateAccuracy() {
        return percentage(dateMatches);
    }

    BigDecimal amountAccuracy() {
        return percentage(amountMatches);
    }

    BigDecimal categoryAccuracy() {
        return percentage(categoryMatches);
    }

    BigDecimal jsonValidityRate() {
        return percentage(validJsonCount);
    }

    BigDecimal validationFailureRate() {
        return percentage(validationFailureCount);
    }

    BigDecimal averageLatencyMs() {
        if (datasetSize == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalLatencyMs)
                .divide(BigDecimal.valueOf(datasetSize), 1, RoundingMode.HALF_UP);
    }

    BigDecimal averageCostUsd() {
        if (datasetSize == 0) {
            return BigDecimal.ZERO;
        }
        return totalEstimatedCostUsd.divide(BigDecimal.valueOf(datasetSize), 4, RoundingMode.HALF_UP);
    }

    BigDecimal averageCostInr() {
        return averageCostUsd().multiply(new BigDecimal("83.00")).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(int numerator) {
        if (datasetSize == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(datasetSize), 1, RoundingMode.HALF_UP);
    }
}
