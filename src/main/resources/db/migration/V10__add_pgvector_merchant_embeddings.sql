DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS vector';

        EXECUTE '
            CREATE TABLE IF NOT EXISTS merchant_embedding (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
                source_text VARCHAR(255) NOT NULL,
                embedding vector(16) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                CONSTRAINT uq_merchant_embedding_merchant_source UNIQUE (merchant_id, source_text)
            )
        ';

        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_merchant_embedding_vector
            ON merchant_embedding
            USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 16)
        ';

        EXECUTE '
            INSERT INTO merchant_embedding (merchant_id, source_text, embedding)
            SELECT id, canonical_name,
                CASE canonical_name
                    WHEN ''Starbucks'' THEN ''[0.456873,0.228436,0.000000,0.000000,0.000000,0.456873,0.652675,0.000000,0.000000,0.000000,0.000000,0.228436,0.000000,0.228436,0.000000,0.000000]''::vector
                    WHEN ''Amazon'' THEN ''[0.000000,0.000000,0.000000,0.000000,0.759190,0.265716,0.531433,0.000000,0.000000,0.265716,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000]''::vector
                    WHEN ''Zomato'' THEN ''[0.286731,0.000000,0.000000,0.286731,0.819232,0.000000,0.000000,0.000000,0.286731,0.000000,0.000000,0.000000,0.286731,0.000000,0.000000,0.000000]''::vector
                    WHEN ''Uber'' THEN ''[0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.330350,0.000000,0.000000,0.330350,0.000000,0.000000,0.000000,0.330350,0.000000,0.826670]''::vector
                    ELSE ''[1.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000]''::vector
                END
            FROM merchants
            ON CONFLICT (merchant_id, source_text) DO NOTHING
        ';

        EXECUTE '
            INSERT INTO merchant_embedding (merchant_id, source_text, embedding)
            SELECT m.id, alias.source_text, alias.embedding::vector
            FROM (
                VALUES
                    (''Starbucks'', ''SQ *STARBUCKS #4821'', ''[0.304276,0.152138,0.000000,0.434680,0.000000,0.304276,0.434680,0.152138,0.000000,0.000000,0.000000,0.152138,0.000000,0.152138,0.586818,0.000000]''),
                    (''Amazon'', ''AMZN MKTP IN'', ''[0.535288,0.187351,0.000000,0.000000,0.000000,0.535288,0.187351,0.187351,0.000000,0.000000,0.535288,0.000000,0.000000,0.000000,0.187351,0.000000]''),
                    (''Zomato'', ''ZOMATO LTD HYD'', ''[0.132052,0.000000,0.000000,0.641395,0.377291,0.000000,0.000000,0.000000,0.132052,0.000000,0.000000,0.000000,0.641395,0.000000,0.000000,0.000000]'')
            ) AS alias(canonical_name, source_text, embedding)
            JOIN merchants m ON m.canonical_name = alias.canonical_name
            ON CONFLICT (merchant_id, source_text) DO NOTHING
        ';
    END IF;
END $$;
