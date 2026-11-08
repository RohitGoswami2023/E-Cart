-- V1__baseline_and_hardening.sql
-- E-Cart PostgreSQL schema baseline & security hardening

-- Ensure pgcrypto or relevant extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: processed_stripe_events (Webhook idempotency)
CREATE TABLE IF NOT EXISTS processed_stripe_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100),
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_stripe_events_event_id ON processed_stripe_events(event_id);

-- Enforce unique constraint on payments.pg_payment_id if payments table exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'payments'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'uk_payments_pg_payment_id'
        ) THEN
            ALTER TABLE payments ADD CONSTRAINT uk_payments_pg_payment_id UNIQUE (pg_payment_id);
        END IF;
    END IF;
END $$;

-- Add stripe_customer_id column to users table if not already present
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'users'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'users' AND column_name = 'stripe_customer_id'
        ) THEN
            ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
        END IF;
    END IF;
END $$;

