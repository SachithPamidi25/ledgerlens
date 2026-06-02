package com.ledgerlens.receipt;

public record ReceiptDuplicateGroup(
        String contentHash,
        long receiptCount
) {
}
