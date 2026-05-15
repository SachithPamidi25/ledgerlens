package com.ledgerlens.ledger;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    boolean existsByReceiptId(UUID receiptId);

    @EntityGraph(attributePaths = {"lines", "lines.account"})
    Optional<JournalEntry> findByReceiptId(UUID receiptId);
}
