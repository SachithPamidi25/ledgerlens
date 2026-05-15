package com.ledgerlens.receipt;

public enum ReceiptStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    DUPLICATE,
    /** DLQ retries exhausted — requires manual review. */
    PERMANENTLY_FAILED
}