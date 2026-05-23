package com.ledgerlens.receipt;

public interface AiExtractionClient {
    ReceiptExtractionResult extractReceiptData(byte[] imageBytes);

    default String providerName() {
        return "unknown";
    }

    default String modelName() {
        return "unknown";
    }
}
