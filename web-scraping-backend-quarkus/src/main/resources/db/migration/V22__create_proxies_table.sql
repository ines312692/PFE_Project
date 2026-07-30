-- ═══════════════════════════════════════════════════════════════════
-- V22__create_proxies_table.sql
--
-- Global proxy pool. Shared across all projects (no project_id FK).
-- Free proxies are ephemeral and replenished by a scheduled job.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE proxies
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    host                VARCHAR(255) NOT NULL,
    port                INT          NOT NULL,
    protocol            VARCHAR(10)  NOT NULL,
    username            VARCHAR(255),
    password            VARCHAR(255),

    country             VARCHAR(2),
    anonymity           VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    source              VARCHAR(40)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    latency_ms          INT,
    success_rate        DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    total_uses          BIGINT       NOT NULL DEFAULT 0,
    successful_uses     BIGINT       NOT NULL DEFAULT 0,

    last_checked_at     TIMESTAMPTZ,
    last_used_at        TIMESTAMPTZ,
    leased_until        TIMESTAMPTZ,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_proxies_protocol  CHECK (protocol  IN ('HTTP','HTTPS','SOCKS4','SOCKS5')),
    CONSTRAINT chk_proxies_anonymity CHECK (anonymity IN ('TRANSPARENT','ANONYMOUS','ELITE','UNKNOWN')),
    CONSTRAINT chk_proxies_source    CHECK (source    IN ('FREE_PROXY_LIST','PROXYSCRAPE','GEONODE','MANUAL')),
    CONSTRAINT chk_proxies_status    CHECK (status    IN ('ACTIVE','INACTIVE','BLACKLISTED')),
    CONSTRAINT chk_proxies_port      CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT chk_proxies_rate      CHECK (success_rate BETWEEN 0.0 AND 1.0),
    CONSTRAINT uq_proxies_endpoint   UNIQUE (host, port, protocol)
);

-- Hot path: acquire a usable proxy fast
CREATE INDEX idx_proxies_acquire
    ON proxies (status, country, protocol, anonymity, latency_ms)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_proxies_least_used
    ON proxies (status, total_uses)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_proxies_fastest
    ON proxies (status, latency_ms)
    WHERE status = 'ACTIVE' AND latency_ms IS NOT NULL;

CREATE INDEX idx_proxies_lease ON proxies (leased_until) WHERE leased_until IS NOT NULL;

COMMENT ON TABLE  proxies                  IS 'Global free-proxy pool. Replenished by a scheduled job; no project isolation.';
COMMENT ON COLUMN proxies.anonymity        IS 'TRANSPARENT | ANONYMOUS | ELITE | UNKNOWN';
COMMENT ON COLUMN proxies.success_rate     IS 'Rolling success ratio in [0.0 ; 1.0]';
COMMENT ON COLUMN proxies.leased_until     IS 'Set on acquire to prevent concurrent reuse; cleared on release.';
