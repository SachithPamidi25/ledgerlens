package com.ledgerlens.receipt;

import java.util.UUID;

public record ReceiptDuplicateMember(
        String contentHash,
        UUID receiptId
) {
}
