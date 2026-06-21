-- Speed up dashboard aggregations
CREATE INDEX IF NOT EXISTS idx_proxies_status      ON proxies (status);
CREATE INDEX IF NOT EXISTS idx_proxies_source      ON proxies (source);
CREATE INDEX IF NOT EXISTS idx_proxies_country     ON proxies (country) WHERE country IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_proxies_status_lat  ON proxies (status, latency_ms);

-- Speed up recent health checks query
CREATE INDEX IF NOT EXISTS idx_health_checked_at   ON proxy_health_checks (checked_at DESC);

-- Speed up anti-bot matrix
CREATE INDEX IF NOT EXISTS idx_antibot_provider    ON proxy_antibot_status (provider);
CREATE INDEX IF NOT EXISTS idx_antibot_status      ON proxy_antibot_status (status);