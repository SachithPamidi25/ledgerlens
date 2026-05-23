package com.ledgerlens.receipt;

public record ReceiptExtractionValidationError(
        String field,
        String message
) {
}
