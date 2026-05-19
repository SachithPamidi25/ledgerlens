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
import java.util.List;
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
        if (journalEntryRepository.existsByReceiptIdAndEntryTypeIn(
                receipt.getId(), List.of(JournalEntryType.ORIGINAL, JournalEntryType.CORRECTION))) {
            log.info("Journal entry already exists for receipt {}", receipt.getId());
            return;
        }

        JournalEntry entry = buildReceiptExpenseEntry(receipt, JournalEntryType.ORIGINAL, 0, null);
        journalEntryRepository.save(entry);
        log.info("Posted balanced journal entry for receipt {} amount={}", receipt.getId(), receipt.getTotal());
    }

    @Transactional
    public JournalEntry reverseEntry(JournalEntry original, String reason) {
        JournalEntry reversal = new JournalEntry();
        reversal.setUser(original.getUser());
        reversal.setReceipt(original.getReceipt());
        reversal.setEntryDate(LocalDate.now());
        reversal.setEntryType(JournalEntryType.REVERSAL);
        reversal.setReversedEntry(original);
        reversal.setCorrectionSequence(original.getCorrectionSequence() + 1);
        reversal.setDescription("Reverse " + original.getDescription() + reasonSuffix(reason));
        reversal.setCurrency(original.getCurrency());

        int lineNumber = 1;
        for (JournalLine originalLine : original.getLines()) {
            reversal.addLine(line(
                    lineNumber++,
                    originalLine.getAccount(),
                    originalLine.getCredit(),
                    originalLine.getDebit()
            ));
        }

        assertBalanced(reversal);
        JournalEntry saved = journalEntryRepository.save(reversal);
        log.info("Posted reversal journal entry {} for original entry {}", saved.getId(), original.getId());
        return saved;
    }

    @Transactional
    public JournalEntry postReceiptCorrection(Receipt receipt, JournalEntry reversedEntry, String reason) {
        if (receipt.getTotal() == null || receipt.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Corrected receipt total must be greater than zero");
        }

        JournalEntry correction = buildReceiptExpenseEntry(
                receipt,
                JournalEntryType.CORRECTION,
                reversedEntry.getCorrectionSequence(),
                "Corrected receipt expense: "
                        + (receipt.getVendor() != null ? receipt.getVendor() : receipt.getOriginalFilename())
                        + reasonSuffix(reason)
        );
        JournalEntry saved = journalEntryRepository.save(correction);
        log.info("Posted corrected journal entry {} for receipt {}", saved.getId(), receipt.getId());
        return saved;
    }

    private JournalEntry buildReceiptExpenseEntry(
            Receipt receipt,
            JournalEntryType entryType,
            int correctionSequence,
            String description
    ) {
        User user = receipt.getUser();
        Account cashAccount = getOrCreateAccount(user, "CASH", "Cash / Bank", AccountType.ASSET);
        MerchantCategory category = receipt.getMerchantCategory() != null ? receipt.getMerchantCategory() : MerchantCategory.OTHER;
        AccountDefinition expenseDefinition = EXPENSE_ACCOUNTS.getOrDefault(category, EXPENSE_ACCOUNTS.get(MerchantCategory.OTHER));
        Account expenseAccount = getOrCreateAccount(user, expenseDefinition.code(), expenseDefinition.name(), AccountType.EXPENSE);

        BigDecimal amount = receipt.getTotal();
        JournalEntry entry = new JournalEntry();
        entry.setUser(user);
        entry.setReceipt(receipt);
        entry.setEntryType(entryType);
        entry.setCorrectionSequence(correctionSequence);
        entry.setEntryDate(receipt.getReceiptDate() != null ? receipt.getReceiptDate() : LocalDate.now());
        entry.setDescription(description != null
                ? description
                : "Receipt expense: " + (receipt.getVendor() != null ? receipt.getVendor() : receipt.getOriginalFilename()));
        entry.setCurrency(receipt.getCurrency() != null ? receipt.getCurrency() : "INR");
        entry.addLine(line(1, expenseAccount, amount, BigDecimal.ZERO));
        entry.addLine(line(2, cashAccount, BigDecimal.ZERO, amount));

        assertBalanced(entry);
        return entry;
    }

    private String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : " (" + reason.trim() + ")";
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
