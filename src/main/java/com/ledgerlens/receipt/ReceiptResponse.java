package com.ledgerlens.receipt;

import com.ledgerlens.ledger.JournalEntryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        String originalFilename,
        ReceiptStatus status,
        String vendor,
        MerchantCategory merchantCategory,
        LocalDate receiptDate,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal tip,
        BigDecimal total,
        String currency,
        JournalEntryResponse journalEntry,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReceiptResponse from(Receipt receipt, JournalEntryResponse journalEntry) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getOriginalFilename(),
                receipt.getStatus(),
                receipt.getVendor(),
                receipt.getMerchantCategory(),
                receipt.getReceiptDate(),
                receipt.getSubtotal(),
                receipt.getTax(),
                receipt.getTip(),
                receipt.getTotal(),
                receipt.getCurrency(),
                journalEntry,
                receipt.getCreatedAt(),
                receipt.getUpdatedAt()
        );
    }
}
