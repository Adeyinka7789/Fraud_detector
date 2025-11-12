CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DROP TABLE IF EXISTS transactions CASCADE;

CREATE TABLE transactions (
    transaction_id UUID DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    bucket_hour TIMESTAMPTZ NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency TEXT NOT NULL,
    merchant_id TEXT NOT NULL,
    risk_score FLOAT,
    decision TEXT NOT NULL,
    features JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    -- Composite primary key for TimescaleDB
    PRIMARY KEY (transaction_id, bucket_hour)
);

-- Convert to hypertable
SELECT create_hypertable('transactions', 'bucket_hour', if_not_exists => TRUE);

-- Unique constraint for Spring Data
CREATE UNIQUE INDEX idx_transactions_unique_id ON transactions(transaction_id);

-- Other indexes
CREATE INDEX IF NOT EXISTS idx_transactions_user_bucket ON transactions(user_id, bucket_hour DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_merchant ON transactions(merchant_id);