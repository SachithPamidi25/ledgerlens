package com.ledgerlens.insights;

import com.ledgerlens.receipt.MerchantCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceiptSearchResult(
        UUID receiptId,
        String vendor,
        MerchantCategory category,
        LocalDate receiptDate,
        BigDecimal total,
        String currency,
        String evidenceText,
        double distance
) {
}
