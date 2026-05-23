package com.ledgerlens.receipt;

public enum ReceiptStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    /** AI extraction parsed but failed backend validation; requires human approval. */
    NEEDS_REVIEW,
    FAILED,
    DUPLICATE,
    /** DLQ retries exhausted — requires manual review. */
    PERMANENTLY_FAILED
}
