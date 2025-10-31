-- Add account-level rate limiting columns to api_user table
ALTER TABLE api_user
ADD COLUMN IF NOT EXISTS account_rate_limit BIGINT NOT NULL DEFAULT 10000,
ADD COLUMN IF NOT EXISTS account_requests_used BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS rate_limit_reset_time TIMESTAMP WITH TIME ZONE;

-- Add key_name column to api_key table
ALTER TABLE api_key
ADD COLUMN IF NOT EXISTS key_name VARCHAR(255);

-- Create api_key_usage table for tracking individual key usage within account quotas
CREATE TABLE IF NOT EXISTS api_key_usage (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    period_type VARCHAR(20) NOT NULL DEFAULT 'hourly',
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_api_key_usage_key_id
        FOREIGN KEY (api_key_id)
        REFERENCES api_key(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_api_key_usage_period
        UNIQUE (api_key_id, period_start, period_type)
);

-- Create index for efficient querying
CREATE INDEX IF NOT EXISTS idx_api_key_usage_key_period
ON api_key_usage(api_key_id, period_start, period_type);

CREATE INDEX IF NOT EXISTS idx_api_key_usage_period_start
ON api_key_usage(period_start);

-- Create trigger to automatically update last_updated timestamp
CREATE OR REPLACE FUNCTION update_api_key_usage_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trg_api_key_usage_last_updated
    BEFORE UPDATE ON api_key_usage
    FOR EACH ROW
    EXECUTE FUNCTION update_api_key_usage_last_updated();