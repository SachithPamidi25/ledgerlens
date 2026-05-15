-- Drop the global unique constraint; same file from different users must be allowed
ALTER TABLE receipts DROP CONSTRAINT IF EXISTS receipts_content_hash_key;

-- Per-user deduplication: same hash is only a duplicate within the same user's receipts
ALTER TABLE receipts ADD CONSTRAINT uq_receipts_content_hash_user
    UNIQUE (content_hash, user_id);