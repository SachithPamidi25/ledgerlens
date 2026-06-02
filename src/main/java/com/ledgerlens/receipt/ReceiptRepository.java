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
            SELECT new com.ledgerlens.receipt.ReceiptStatusSummaryResponse(
                COUNT(r),
                SUM(CASE WHEN r.status = com.ledgerlens.receipt.ReceiptStatus.COMPLETED THEN 1 ELSE 0 END),
                SUM(CASE WHEN r.status IN (
                    com.ledgerlens.receipt.ReceiptStatus.PENDING,
                    com.ledgerlens.receipt.ReceiptStatus.PROCESSING
                ) THEN 1 ELSE 0 END),
                SUM(CASE WHEN r.status IN (
                    com.ledgerlens.receipt.ReceiptStatus.FAILED,
                    com.ledgerlens.receipt.ReceiptStatus.PERMANENTLY_FAILED
                ) THEN 1 ELSE 0 END),
                SUM(CASE WHEN r.status = com.ledgerlens.receipt.ReceiptStatus.NEEDS_REVIEW THEN 1 ELSE 0 END),
                SUM(CASE WHEN r.status = com.ledgerlens.receipt.ReceiptStatus.DUPLICATE THEN 1 ELSE 0 END),
                COALESCE(SUM(CASE
                    WHEN r.status = com.ledgerlens.receipt.ReceiptStatus.COMPLETED AND r.total IS NOT NULL
                    THEN r.total
                    ELSE 0
                END), 0)
            )
            FROM Receipt r
            WHERE r.user.id = :userId
            """)
    ReceiptStatusSummaryResponse summarizeStatuses(@Param("userId") UUID userId);

    @Query("""
            SELECT new com.ledgerlens.receipt.ReceiptDuplicateGroup(r.contentHash, COUNT(r))
            FROM Receipt r
            WHERE r.user.id = :userId
              AND r.contentHash IS NOT NULL
              AND r.contentHash <> ''
            GROUP BY r.contentHash
            HAVING COUNT(r) > 1
            """)
    List<ReceiptDuplicateGroup> findDuplicateReceiptGroups(@Param("userId") UUID userId);

    @Query("""
            SELECT new com.ledgerlens.receipt.ReceiptDuplicateMember(r.contentHash, r.id)
            FROM Receipt r
            WHERE r.user.id = :userId
              AND r.contentHash IN :contentHashes
            ORDER BY r.contentHash ASC, r.createdAt ASC
            """)
    List<ReceiptDuplicateMember> findDuplicateReceiptMembers(
            @Param("userId") UUID userId,
            @Param("contentHashes") List<String> contentHashes
    );

    @Query("""
            SELECT r FROM Receipt r
            WHERE r.user.id = :userId
              AND r.total IS NOT NULL
              AND r.total >= :threshold
            ORDER BY r.total DESC
            """)
    List<Receipt> findHighValueTransactions(
            @Param("userId") UUID userId,
            @Param("threshold") java.math.BigDecimal threshold,
            Pageable pageable
    );

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
