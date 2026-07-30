-- V26__create_etl_exports_audit_table.sql
-- Audit trail des exports effectués sur les résultats de scrape

CREATE TABLE IF NOT EXISTS etl_exports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scrape_result_id UUID NOT NULL REFERENCES scrape_results(id) ON DELETE CASCADE,
    format        VARCHAR(20)  NOT NULL,
    destination   VARCHAR(30)  NOT NULL,
    record_count  INTEGER      NOT NULL DEFAULT 0,
    duration_ms   BIGINT       NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',  -- COMPLETED | FAILED
    error_message TEXT,
    download_url  TEXT,
    exported_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    exported_by   UUID         REFERENCES users(id)
);

CREATE INDEX idx_etl_exports_scrape_result ON etl_exports(scrape_result_id);
CREATE INDEX idx_etl_exports_exported_at   ON etl_exports(exported_at DESC);

COMMENT ON TABLE etl_exports IS
    'Audit trail of all data exports performed after ETL pipeline processing.';
