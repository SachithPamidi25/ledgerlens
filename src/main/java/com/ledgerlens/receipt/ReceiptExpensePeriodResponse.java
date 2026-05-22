package com.ledgerlens.receipt;

import java.math.BigDecimal;

public record ReceiptExpensePeriodResponse(
        String key,
        String label,
        String periodType,
        long receiptCount,
        BigDecimal total
) {
}
