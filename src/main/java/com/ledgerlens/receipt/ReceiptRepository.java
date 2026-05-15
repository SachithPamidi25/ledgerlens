package com.ledgerlens.receipt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Page<Receipt> findByUserId(UUID userId, Pageable pageable);

    List<Receipt> findAllByUserId(UUID userId);

    Optional<Receipt> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    int deleteByIdAndUserId(UUID id, UUID userId);

    @Modifying
    int deleteByUserId(UUID userId);

    boolean existsByContentHashAndUserId(String contentHash, UUID userId);

    @Query("""
            SELECT r FROM Receipt r
            WHERE r.user.id = :userId
              AND r.status = com.ledgerlens.receipt.ReceiptStatus.COMPLETED
              AND r.receiptDate IS NOT NULL
            """)
    List<Receipt> findCompletedForSummary(@Param("userId") UUID userId);

    @Query("""
            SELECT r FROM Receipt r
            WHERE r.user.id = :userId
              AND r.status = com.ledgerlens.receipt.ReceiptStatus.COMPLETED
              AND r.receiptDate IS NOT NULL
              AND r.receiptDate >= :fromDate
              AND r.receiptDate < :toDate
            """)
    List<Receipt> findCompletedForSummaryBetween(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT r FROM Receipt r
            WHERE r.user.id = :userId
              AND r.status = com.ledgerlens.receipt.ReceiptStatus.COMPLETED
              AND r.receiptDate IS NOT NULL
              AND r.receiptDate >= :fromDate
            ORDER BY r.receiptDate DESC
            """)
    List<Receipt> findCompletedSince(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate
    );
}
