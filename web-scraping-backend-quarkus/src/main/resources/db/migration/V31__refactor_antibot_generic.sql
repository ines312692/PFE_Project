-- 1. Create the generic anti-bot status table
CREATE TABLE proxy_antibot_status
(
    proxy_id         UUID        NOT NULL REFERENCES proxies (id) ON DELETE CASCADE,
    provider         VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'UNTESTED',
    detected         BOOLEAN,
    blocked          BOOLEAN,
    last_status_code INTEGER,
    last_checked_at  TIMESTAMP,
    last_error       VARCHAR(500),
    PRIMARY KEY (proxy_id, provider)
);

CREATE INDEX idx_proxy_antibot_provider_status
    ON proxy_antibot_status (provider, status);

CREATE INDEX idx_proxy_antibot_last_checked
    ON proxy_antibot_status (last_checked_at);

-- 2. Migrate existing Cloudflare data into the generic table
INSERT INTO proxy_antibot_status (proxy_id, provider, status, last_checked_at)
SELECT id, 'CLOUDFLARE', cloudflare_status, last_cloudflare_check_at
FROM proxies
WHERE cloudflare_status IS NOT NULL;

-- 3. Drop the old Cloudflare-specific columns from proxies
DROP INDEX IF EXISTS idx_proxies_cloudflare_status;
ALTER TABLE proxies DROP COLUMN cloudflare_status;
ALTER TABLE proxies DROP COLUMN last_cloudflare_check_at;