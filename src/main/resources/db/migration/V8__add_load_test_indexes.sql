CREATE INDEX IF NOT EXISTS idx_receipts_user_created
    ON receipts(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_receipts_user_status_date
    ON receipts(user_id, status, receipt_date DESC)
    WHERE receipt_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_receipts_user_status_updated
    ON receipts(user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_id
    ON outbox_events(aggregate_id);

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed_created_id
    ON outbox_events(created_at, id)
    WHERE processed_at IS NULL;
