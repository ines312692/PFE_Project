-- ═══════════════════════════════════════════════════════════════════
-- V25__create_proxy_usage_table.sql
--
-- Audit trail: which proxy served which job and how it went.
-- Useful to debug "why did job X fail?" and to feed proxy stats.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE proxy_usage
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proxy_id        UUID         NOT NULL,
    job_id          UUID         NOT NULL,
    success         BOOLEAN      NOT NULL,
    latency_ms      INT,
    error_message   VARCHAR(500),
    used_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_proxy_usage_proxy
        FOREIGN KEY (proxy_id) REFERENCES proxies (id) ON DELETE CASCADE,
    CONSTRAINT fk_proxy_usage_job
        FOREIGN KEY (job_id)   REFERENCES jobs    (id) ON DELETE CASCADE
);

CREATE INDEX idx_proxy_usage_job   ON proxy_usage (job_id, used_at DESC);
CREATE INDEX idx_proxy_usage_proxy ON proxy_usage (proxy_id, used_at DESC);

COMMENT ON TABLE proxy_usage IS 'Per-job proxy audit. Cleaned with the job (CASCADE).';
