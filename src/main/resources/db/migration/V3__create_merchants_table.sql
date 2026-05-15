CREATE TABLE merchants (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           canonical_name VARCHAR(255) NOT NULL UNIQUE,
                           category VARCHAR(100),
                           created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_merchants_canonical_name_trgm
    ON merchants USING gin(canonical_name gin_trgm_ops);

INSERT INTO merchants (canonical_name, category) VALUES
                                                     ('Starbucks', 'FOOD'),
                                                     ('McDonald''s', 'FOOD'),
                                                     ('Domino''s', 'FOOD'),
                                                     ('Swiggy', 'FOOD'),
                                                     ('Zomato', 'FOOD'),
                                                     ('Amazon', 'SHOPPING'),
                                                     ('Flipkart', 'SHOPPING'),
                                                     ('Uber', 'TRANSPORT'),
                                                     ('Ola', 'TRANSPORT'),
                                                     ('Rapido', 'TRANSPORT'),
                                                     ('Netflix', 'ENTERTAINMENT'),
                                                     ('Spotify', 'ENTERTAINMENT'),
                                                     ('Freelancer', 'OTHER'),
                                                     ('Upwork', 'OTHER');