CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_user_code UNIQUE (user_id, code)
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    receipt_id UUID UNIQUE REFERENCES receipts(id) ON DELETE CASCADE,
    entry_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_journal_entries_user_id ON journal_entries(user_id);
CREATE INDEX idx_journal_entries_receipt_id ON journal_entries(receipt_id);

CREATE TABLE journal_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    line_number INTEGER NOT NULL,
    debit DECIMAL(12,2) NOT NULL DEFAULT 0,
    credit DECIMAL(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_journal_lines_entry_line UNIQUE (journal_entry_id, line_number),
    CONSTRAINT chk_journal_line_single_side CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
);

CREATE INDEX idx_journal_lines_entry_id ON journal_lines(journal_entry_id);
CREATE INDEX idx_journal_lines_account_id ON journal_lines(account_id);
