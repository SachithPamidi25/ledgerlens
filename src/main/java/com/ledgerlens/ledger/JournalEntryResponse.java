package com.ledgerlens.ledger;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        LocalDate entryDate,
        JournalEntryType entryType,
        UUID reversedEntryId,
        int correctionSequence,
        String description,
        String currency,
        List<JournalLineResponse> lines
) {
    public static JournalEntryResponse from(JournalEntry entry) {
        return new JournalEntryResponse(
                entry.getId(),
                entry.getEntryDate(),
                entry.getEntryType(),
                entry.getReversedEntry() != null ? entry.getReversedEntry().getId() : null,
                entry.getCorrectionSequence(),
                entry.getDescription(),
                entry.getCurrency(),
                entry.getLines().stream().map(JournalLineResponse::from).toList()
        );
    }
}
