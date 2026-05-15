package com.ledgerlens.ledger;

import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.Receipt;
import com.ledgerlens.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPostingService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    private static final Map<MerchantCategory, AccountDefinition> EXPENSE_ACCOUNTS = Map.of(
            MerchantCategory.FOOD, new AccountDefinition("EXP_FOOD", "Food Expense"),
            MerchantCategory.TRANSPORT, new AccountDefinition("EXP_TRANSPORT", "Transport Expense"),
            MerchantCategory.SHOPPING, new AccountDefinition("EXP_SHOPPING", "Shopping Expense"),
            MerchantCategory.ENTERTAINMENT, new AccountDefinition("EXP_ENTERTAINMENT", "Entertainment Expense"),
            MerchantCategory.HEALTH, new AccountDefinition("EXP_HEALTH", "Health Expense"),
            MerchantCategory.UTILITIES, new AccountDefinition("EXP_UTILITIES", "Utilities Expense"),
            MerchantCategory.OTHER, new AccountDefinition("EXP_OTHER", "Other Expense")
    );

    @Transactional
    public void postReceiptExpense(Receipt receipt) {
        if (receipt.getTotal() == null || receipt.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Skipping journal posting for receipt {} because total is missing or non-positive", receipt.getId());
            return;
        }
        if (journalEntryRepository.existsByReceiptId(receipt.getId())) {
            log.info("Journal entry already exists for receipt {}", receipt.getId());
            return;
        }

        User user = receipt.getUser();
        Account cashAccount = getOrCreateAccount(user, "CASH", "Cash / Bank", AccountType.ASSET);
        MerchantCategory category = receipt.getMerchantCategory() != null ? receipt.getMerchantCategory() : MerchantCategory.OTHER;
        AccountDefinition expenseDefinition = EXPENSE_ACCOUNTS.getOrDefault(category, EXPENSE_ACCOUNTS.get(MerchantCategory.OTHER));
        Account expenseAccount = getOrCreateAccount(user, expenseDefinition.code(), expenseDefinition.name(), AccountType.EXPENSE);

        BigDecimal amount = receipt.getTotal();
        JournalEntry entry = new JournalEntry();
        entry.setUser(user);
        entry.setReceipt(receipt);
        entry.setEntryDate(receipt.getReceiptDate() != null ? receipt.getReceiptDate() : LocalDate.now());
        entry.setDescription("Receipt expense: " + (receipt.getVendor() != null ? receipt.getVendor() : receipt.getOriginalFilename()));
        entry.setCurrency(receipt.getCurrency() != null ? receipt.getCurrency() : "INR");
        entry.addLine(line(1, expenseAccount, amount, BigDecimal.ZERO));
        entry.addLine(line(2, cashAccount, BigDecimal.ZERO, amount));

        assertBalanced(entry);
        journalEntryRepository.save(entry);
        log.info("Posted balanced journal entry for receipt {} amount={}", receipt.getId(), amount);
    }

    private Account getOrCreateAccount(User user, String code, String name, AccountType type) {
        return accountRepository.findByUserIdAndCode(user.getId(), code)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setUser(user);
                    account.setCode(code);
                    account.setName(name);
                    account.setType(type);
                    return accountRepository.save(account);
                });
    }

    private JournalLine line(int lineNumber, Account account, BigDecimal debit, BigDecimal credit) {
        JournalLine line = new JournalLine();
        line.setLineNumber(lineNumber);
        line.setAccount(account);
        line.setDebit(debit);
        line.setCredit(credit);
        return line;
    }

    private void assertBalanced(JournalEntry entry) {
        BigDecimal totalDebits = entry.getLines().stream()
                .map(JournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = entry.getLines().stream()
                .map(JournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException("Journal entry is unbalanced: debits=" + totalDebits + ", credits=" + totalCredits);
        }
    }

    private record AccountDefinition(String code, String name) {
    }
}
