CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

-- Partial index — only unprocessed rows are queried by the poller
CREATE INDEX idx_outbox_unprocessed ON outbox_events(created_at)
    WHERE processed_at IS NULL;
