ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS journal_entries_receipt_id_key;

ALTER TABLE journal_entries
    ADD COLUMN entry_type VARCHAR(30) NOT NULL DEFAULT 'ORIGINAL',
    ADD COLUMN reversed_entry_id UUID REFERENCES journal_entries(id),
    ADD COLUMN correction_sequence INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_journal_entries_receipt_type_created
    ON journal_entries(receipt_id, entry_type, created_at DESC);

CREATE INDEX idx_journal_entries_reversed_entry_id
    ON journal_entries(reversed_entry_id);
