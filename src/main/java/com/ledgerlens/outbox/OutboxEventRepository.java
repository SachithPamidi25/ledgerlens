package com.ledgerlens.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Selects a bounded batch of unprocessed events with row-level locking.
     * SKIP LOCKED ensures concurrent poller instances (multiple app replicas)
     * never process the same event — each instance grabs its own exclusive batch.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE processed_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findUnprocessedForUpdate(int batchSize);

    void deleteByAggregateId(UUID aggregateId);
}
