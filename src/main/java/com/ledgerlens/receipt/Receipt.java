package com.ledgerlens.receipt;

import com.ledgerlens.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString(exclude = "user")
@Entity
@Table(name = "receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_receipts_content_hash_user", columnNames = {"content_hash", "user_id"})
})
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptStatus status = ReceiptStatus.PENDING;

    @Column
    private String vendor;

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_category")
    private MerchantCategory merchantCategory;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(precision = 10, scale = 2)
    private BigDecimal tip;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    @Column
    private String currency = "INR";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_extraction", columnDefinition = "jsonb")
    private String rawExtraction;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Receipt receipt)) return false;
        return id != null && id.equals(receipt.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
