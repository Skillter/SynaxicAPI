-- Create stats table for persistent counters
CREATE TABLE IF NOT EXISTS api_stats (
    id BIGSERIAL PRIMARY KEY,
    counter_name VARCHAR(255) UNIQUE NOT NULL,
    counter_value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert initial counter for total API requests
INSERT INTO api_stats (counter_name, counter_value) VALUES ('total_api_requests', 0)
ON CONFLICT (counter_name) DO NOTHING;
