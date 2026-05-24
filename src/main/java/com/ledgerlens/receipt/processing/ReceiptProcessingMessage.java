package com.ledgerlens.receipt.processing;

import java.util.UUID;

public record ReceiptProcessingMessage(
        UUID receiptId,
        String storageKey,
        UUID userId,
        String traceId
) {}
