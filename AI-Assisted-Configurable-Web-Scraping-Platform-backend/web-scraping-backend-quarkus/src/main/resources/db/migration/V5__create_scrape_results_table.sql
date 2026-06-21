-- ============================================================
-- V5__create_scrape_results_table.sql
-- Stores scraping results received from the Python engine
-- ============================================================

CREATE TABLE scrape_results (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID        NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL DEFAULT 'completed',
    records         JSONB       NOT NULL DEFAULT '[]',
    errors          JSONB       NOT NULL DEFAULT '[]',
    pages_crawled   INT         NOT NULL DEFAULT 0,
    duration_ms     INT         NOT NULL DEFAULT 0,
    engine_used     VARCHAR(50),
    scraped_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_scrape_results_status
        CHECK (status IN ('completed', 'failed', 'partial'))
);

CREATE INDEX idx_scrape_results_job_id ON scrape_results (job_id);