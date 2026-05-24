package com.ledgerlens.receipt.eval;

import com.ledgerlens.receipt.extraction.ReceiptExtractionResult;

import java.nio.file.Path;

record ReceiptExtractionEvalCase(
        String id,
        Path receiptPath,
        ReceiptExtractionResult expected
) {
}
