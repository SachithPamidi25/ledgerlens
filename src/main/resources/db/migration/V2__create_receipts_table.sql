CREATE TABLE receipts (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id UUID NOT NULL REFERENCES users(id),
                          original_filename VARCHAR(255) NOT NULL,
                          storage_key VARCHAR(500) NOT NULL,
                          status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                          vendor VARCHAR(255),
                          merchant_category VARCHAR(100),
                          receipt_date DATE,
                          subtotal DECIMAL(10,2),
                          tax DECIMAL(10,2),
                          tip DECIMAL(10,2),
                          total DECIMAL(10,2),
                          currency VARCHAR(10) DEFAULT 'INR',
                          raw_extraction JSONB,
                          content_hash VARCHAR(64) UNIQUE,
                          created_at TIMESTAMP NOT NULL DEFAULT now(),
                          updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_receipts_user_id ON receipts(user_id);
CREATE INDEX idx_receipts_status ON receipts(status);
CREATE INDEX idx_receipts_content_hash ON receipts(content_hash);