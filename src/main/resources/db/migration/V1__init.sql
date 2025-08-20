CREATE TABLE "user" (
    id BIGSERIAL PRIMARY KEY,
    google_sub VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE api_key (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    prefix VARCHAR(16) NOT NULL UNIQUE,
    key_hash VARCHAR(64) NOT NULL, -- SHA-256 of full key
    quota_limit INT NOT NULL DEFAULT 1000,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP
);

CREATE INDEX idx_api_key_prefix ON api_key(prefix);
CREATE INDEX idx_user_google_sub ON "user"(google_sub);