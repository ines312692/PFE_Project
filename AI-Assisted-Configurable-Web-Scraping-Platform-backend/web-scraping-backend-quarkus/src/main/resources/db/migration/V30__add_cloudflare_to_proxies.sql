-- Add Cloudflare detection columns to proxies
ALTER TABLE proxies
    ADD COLUMN cloudflare_status VARCHAR(20) NOT NULL DEFAULT 'UNTESTED',
    ADD COLUMN last_cloudflare_check_at TIMESTAMP NULL;

CREATE INDEX idx_proxies_cloudflare_status ON proxies (cloudflare_status);

-- Enrich health checks with Cloudflare info
ALTER TABLE proxy_health_checks
    ADD COLUMN cloudflare_detected BOOLEAN NULL,
    ADD COLUMN cloudflare_blocked  BOOLEAN     NULL,
    ADD COLUMN http_status_code    INTEGER     NULL,
    ADD COLUMN check_type          VARCHAR(20) NOT NULL DEFAULT 'SOCKET';
-- check_type: SOCKET | HTTP | CLOUDFLARE | FULL