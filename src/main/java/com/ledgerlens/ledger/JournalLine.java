package com.ledgerlens.ledger;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@ToString(exclude = {"journalEntry", "account"})
@Entity
@Table(name = "journal_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uq_journal_lines_entry_line", columnNames = {"journal_entry_id", "line_number"})
})
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;
}
