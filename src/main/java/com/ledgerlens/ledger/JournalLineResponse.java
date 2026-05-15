package com.ledgerlens.ledger;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLineResponse(
        UUID accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        BigDecimal debit,
        BigDecimal credit
) {
    public static JournalLineResponse from(JournalLine line) {
        Account account = line.getAccount();
        return new JournalLineResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                line.getDebit(),
                line.getCredit()
        );
    }
}
