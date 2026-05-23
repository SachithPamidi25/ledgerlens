DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS vector';

        EXECUTE '
            CREATE TABLE IF NOT EXISTS receipt_embedding (
                receipt_id UUID PRIMARY KEY REFERENCES receipts(id) ON DELETE CASCADE,
                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                search_text TEXT NOT NULL,
                embedding vector(16) NOT NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT now()
            )
        ';

        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_receipt_embedding_user
            ON receipt_embedding(user_id)
        ';

        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_receipt_embedding_vector
            ON receipt_embedding
            USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 32)
        ';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS insight_source_receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receipt_id UUID NOT NULL REFERENCES receipts(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    answer_excerpt TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_insight_source_receipt_user_created
ON insight_source_receipt(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_insight_source_receipt_receipt
ON insight_source_receipt(receipt_id);
