-- Add index on api_user.email for faster login lookups
-- Email lookups are frequent (every OAuth login) but were unindexed
CREATE INDEX IF NOT EXISTS idx_api_user_email ON api_user(email);

-- Add composite index for api_key_usage queries
-- Improves performance of batch queries for multiple API keys
CREATE INDEX IF NOT EXISTS idx_api_key_usage_key_id_type
ON api_key_usage(api_key_id, period_type);
