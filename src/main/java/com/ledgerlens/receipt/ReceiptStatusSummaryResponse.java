package com.ledgerlens.receipt;

import java.math.BigDecimal;

public record ReceiptStatusSummaryResponse(
        long total,
        long completed,
        long processing,
        long failed,
        long needsReview,
        long duplicate,
        BigDecimal completedSpend
) {
}
