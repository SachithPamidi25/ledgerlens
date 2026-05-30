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
        String failureReason,
        JournalEntryResponse journalEntry,
        java.util.List<JournalEntryResponse> journalEntries,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReceiptResponse from(Receipt receipt, JournalEntryResponse journalEntry) {
        return from(receipt, journalEntry, journalEntry == null ? java.util.List.of() : java.util.List.of(journalEntry));
    }

    public static ReceiptResponse from(
            Receipt receipt,
            JournalEntryResponse journalEntry,
            java.util.List<JournalEntryResponse> journalEntries
    ) {
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
                receipt.getFailureReason(),
                journalEntry,
                journalEntries,
                receipt.getCreatedAt(),
                receipt.getUpdatedAt()
        );
    }
}
