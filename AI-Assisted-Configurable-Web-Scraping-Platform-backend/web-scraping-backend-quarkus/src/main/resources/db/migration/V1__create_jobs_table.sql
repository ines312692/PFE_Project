-- ============================================================
-- V1__create_jobs_table.sql
-- Scraping job configuration table
-- Covers: F-1.1 to F-1.7, F-2.1, F-2.3 to F-2.6, F-7.1, F-7.2
-- ============================================================

CREATE TABLE jobs (
    -- Identity
                      id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Basic metadata
                      name                        VARCHAR(255)    NOT NULL,
                      description                 VARCHAR(1000),

    -- Target
                      target_url                  VARCHAR(2048)   NOT NULL,
                      seed_urls                   TEXT,                           -- JSON array of additional seed URLs

    -- Extraction
                      schema_id                   UUID,                           -- FK to extraction_schemas (future)
                      extraction_mode             VARCHAR(30)     NOT NULL DEFAULT 'CSS_SELECTOR',
                      llm_extraction_prompt       TEXT,                           -- used when extraction_mode = LLM

    -- Scheduling (F-1.3)
                      trigger_type                VARCHAR(20)     NOT NULL DEFAULT 'MANUAL',
                      cron_expression             VARCHAR(100),                   -- used when trigger_type = SCHEDULED
                      webhook_secret              VARCHAR(255),                   -- used when trigger_type = WEBHOOK

    -- Lifecycle (F-1.4)
                      status                      VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
                      priority                    VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
                      next_run_at                 TIMESTAMPTZ,
                      last_run_at                 TIMESTAMPTZ,
                      execution_count             INT             NOT NULL DEFAULT 0,

    -- Dry-run (F-1.7)
                      dry_run_enabled             BOOLEAN         NOT NULL DEFAULT FALSE,
                      dry_run_page_limit          INT             NOT NULL DEFAULT 5,

    -- Template & duplication (F-1.5)
                      is_template                 BOOLEAN         NOT NULL DEFAULT FALSE,
                      template_category           VARCHAR(50),
                      source_job_id               UUID,                           -- FK to jobs (self-reference)

    -- Output (F-2.6)
                      output_formats              VARCHAR(100)    NOT NULL DEFAULT 'JSON',
                      output_collection_name      VARCHAR(255),

    -- ── CrawlConfig (F-1.6, F-2.1, F-2.2, F-2.5, F-3.3) ─────────────────
                      js_rendering_enabled        BOOLEAN         NOT NULL DEFAULT FALSE,
                      max_pages                   INT             NOT NULL DEFAULT 100,
                      max_depth                   INT             NOT NULL DEFAULT 2,
                      url_include_patterns        VARCHAR(2000),
                      url_exclude_patterns        VARCHAR(2000),
                      pagination_type             VARCHAR(30)     NOT NULL DEFAULT 'NONE',
                      pagination_selector         VARCHAR(500),
                      max_pagination_pages        INT             NOT NULL DEFAULT 50,
                      request_delay_ms            INT             NOT NULL DEFAULT 1000,
                      request_delay_jitter_ms     INT             NOT NULL DEFAULT 500,
                      request_timeout_ms          INT             NOT NULL DEFAULT 30000,
                      session_enabled             BOOLEAN         NOT NULL DEFAULT FALSE,
                      follow_redirects            BOOLEAN         NOT NULL DEFAULT TRUE,
                      user_agent    VARCHAR(500),

    -- ── RetryPolicy (F-7.1, F-7.2) ───────────────────────────────────────
                      max_retries                 INT             NOT NULL DEFAULT 3,
                      retry_initial_delay_ms      INT             NOT NULL DEFAULT 1000,
                      retry_backoff_multiplier    DOUBLE PRECISION NOT NULL DEFAULT 2.0,
                      retry_max_delay_ms          INT             NOT NULL DEFAULT 60000,
                      circuit_breaker_enabled     BOOLEAN         NOT NULL DEFAULT TRUE,
                      circuit_breaker_threshold   DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                      circuit_breaker_reset_seconds INT           NOT NULL DEFAULT 60,

    -- Ownership & audit
                      created_by                  UUID            NOT NULL,
                      updated_by                  UUID,
                      created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                      updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Soft delete
                      deleted                     BOOLEAN         NOT NULL DEFAULT FALSE,
                      deleted_at                  TIMESTAMPTZ,

    -- Constraints
                      CONSTRAINT chk_jobs_status
                          CHECK (status IN ('CREATED','QUEUED','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLED')),
                      CONSTRAINT chk_jobs_trigger_type
                          CHECK (trigger_type IN ('MANUAL','SCHEDULED','WEBHOOK')),
                      CONSTRAINT chk_jobs_extraction_mode
                          CHECK (extraction_mode IN ('CSS_SELECTOR','XPATH','REGEX','LLM','VISUAL','AUTO')),
                      CONSTRAINT chk_jobs_priority
                          CHECK (priority IN ('LOW','NORMAL','HIGH','CRITICAL')),
                      CONSTRAINT chk_jobs_pagination_type
                          CHECK (pagination_type IN ('NONE','NUMBERED','INFINITE_SCROLL','LOAD_MORE','NEXT_BUTTON','CURSOR')),
                      CONSTRAINT chk_jobs_max_depth
                          CHECK (max_depth BETWEEN 0 AND 20),
                      CONSTRAINT chk_jobs_dry_run_page_limit
                          CHECK (dry_run_page_limit BETWEEN 1 AND 100),
                      CONSTRAINT chk_jobs_circuit_breaker_threshold
                          CHECK (circuit_breaker_threshold BETWEEN 0.1 AND 1.0),
                      CONSTRAINT chk_jobs_backoff_multiplier
                          CHECK (retry_backoff_multiplier BETWEEN 1.0 AND 5.0)
);

-- Indexes
CREATE INDEX idx_jobs_status       ON jobs (status)       WHERE deleted = FALSE;
CREATE INDEX idx_jobs_created_by   ON jobs (created_by)   WHERE deleted = FALSE;
CREATE INDEX idx_jobs_created_at   ON jobs (created_at DESC);
CREATE INDEX idx_jobs_next_run_at  ON jobs (next_run_at)   WHERE deleted = FALSE AND trigger_type = 'SCHEDULED';
CREATE INDEX idx_jobs_trigger_type ON jobs (trigger_type)  WHERE deleted = FALSE;
CREATE INDEX idx_jobs_is_template  ON jobs (is_template)   WHERE is_template = TRUE AND deleted = FALSE;

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_jobs_updated_at
    BEFORE UPDATE ON jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();