package com.ledgerlens.receipt;

import java.util.UUID;

public record UploadUrlResponse(
        UUID receiptId,
        String uploadUrl,
        String storageKey
) {}