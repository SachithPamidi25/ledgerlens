package com.ledgerlens.receipt;

public interface AiExtractionClient {
    ReceiptExtractionResult extractReceiptData(byte[] imageBytes);
}
