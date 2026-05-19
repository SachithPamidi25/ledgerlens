package com.ledgerlens.ledger;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    boolean existsByReceiptIdAndEntryTypeIn(UUID receiptId, Collection<JournalEntryType> entryTypes);

    @EntityGraph(attributePaths = {"lines", "lines.account"})
    Optional<JournalEntry> findFirstByReceiptIdAndEntryTypeInOrderByCreatedAtDesc(
            UUID receiptId, Collection<JournalEntryType> entryTypes);

    @EntityGraph(attributePaths = {"lines", "lines.account"})
    List<JournalEntry> findByReceiptIdOrderByCreatedAtDesc(UUID receiptId);

    int countByReceiptId(UUID receiptId);
}
