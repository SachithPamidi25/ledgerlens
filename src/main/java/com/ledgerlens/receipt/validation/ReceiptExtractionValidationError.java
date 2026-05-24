package com.ledgerlens.receipt.validation;

public record ReceiptExtractionValidationError(
        String field,
        String message
) {
}
