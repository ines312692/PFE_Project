-- ═══════════════════════════════════════════════════════════════════
-- V23__create_proxy_health_checks_table.sql
--
-- History of every health check ran against a proxy. Used to compute
-- rolling stats and to debug "why was this proxy blacklisted?".
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE proxy_health_checks
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proxy_id        UUID         NOT NULL,
    success         BOOLEAN      NOT NULL,
    latency_ms      INT,
    error_message   VARCHAR(500),
    checked_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_proxy_health_checks_proxy
        FOREIGN KEY (proxy_id) REFERENCES proxies (id) ON DELETE CASCADE
);

CREATE INDEX idx_proxy_health_checks_proxy_time
    ON proxy_health_checks (proxy_id, checked_at DESC);

-- Cleanup helper: keep last 7 days of history
COMMENT ON TABLE proxy_health_checks IS 'Health-check audit log. Kept for 7 days, older rows cleaned by a scheduled job.';
