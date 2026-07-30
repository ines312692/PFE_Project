-- ═══════════════════════════════════════════════════════════════════
-- V24__create_proxy_configs_table.sql
--
-- Per-job proxy configuration. One-to-one with jobs, same pattern
-- as browser_configs and retry_policies. Decoupled from the global
-- pool so that disabling proxies on a job does not touch the pool.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE proxy_configs
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID         NOT NULL UNIQUE,

    enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    rotation_strategy   VARCHAR(30)  NOT NULL DEFAULT 'RANDOM',

    countries           TEXT,
    protocols           TEXT,
    anonymity_levels    TEXT,

    max_latency_ms      INT,
    min_success_rate    DOUBLE PRECISION,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_proxy_configs_job
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,

    CONSTRAINT chk_proxy_configs_strategy
        CHECK (rotation_strategy IN ('RANDOM','ROUND_ROBIN','LEAST_USED','FASTEST')),

    CONSTRAINT chk_proxy_configs_min_rate
        CHECK (min_success_rate IS NULL OR (min_success_rate BETWEEN 0.0 AND 1.0))
);

CREATE INDEX idx_proxy_configs_job ON proxy_configs (job_id);

COMMENT ON COLUMN proxy_configs.countries        IS 'CSV of ISO-2 country codes; null = any';
COMMENT ON COLUMN proxy_configs.protocols        IS 'CSV of HTTP|HTTPS|SOCKS4|SOCKS5; null = any';
COMMENT ON COLUMN proxy_configs.anonymity_levels IS 'CSV of TRANSPARENT|ANONYMOUS|ELITE; null = any';
