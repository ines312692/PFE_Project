-- V18__create_etl_runs_table.sql
-- Audit table: tracks every ETL pipeline execution (non-destructive, original records preserved)

CREATE TABLE etl_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scrape_result_id UUID NOT NULL REFERENCES scrape_results(id) ON DELETE CASCADE,
    steps           JSONB NOT NULL DEFAULT '[]',
    raw_count       INT  NOT NULL DEFAULT 0,
    clean_count     INT  NOT NULL DEFAULT 0,
    removed_count   INT  NOT NULL DEFAULT 0,
    duration_ms     BIGINT,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_by      UUID,
    saved           BOOLEAN NOT NULL DEFAULT FALSE   -- true = cleaned records were persisted
);

CREATE INDEX idx_etl_runs_scrape_result ON etl_runs(scrape_result_id);
CREATE INDEX idx_etl_runs_applied_at    ON etl_runs(applied_at DESC);
