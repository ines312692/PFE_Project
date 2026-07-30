-- ============================================================
-- V7__split_job_types.sql
-- Split jobs into SCRAPER / CRAWLER with dedicated config tables
-- ============================================================

-- 1. Add job_type discriminator to jobs
ALTER TABLE jobs ADD COLUMN job_type VARCHAR(20) NOT NULL DEFAULT 'SCRAPER';
ALTER TABLE jobs ADD CONSTRAINT chk_jobs_job_type CHECK (job_type IN ('SCRAPER', 'CRAWLER'));

-- 2. Create scraper_configs table
CREATE TABLE scraper_configs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id                  UUID NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,

    seed_urls               TEXT,                           -- JSON array of URLs to scrape
    single_page_mode        BOOLEAN         NOT NULL DEFAULT TRUE,
    wait_for_selector       VARCHAR(500),                   -- CSS selector to wait for before extraction
    scroll_to_bottom        BOOLEAN         NOT NULL DEFAULT FALSE,
    click_before_extract    VARCHAR(500),                   -- selector to click before extracting
    llm_extraction_prompt   TEXT,                           -- moved from jobs table

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scraper_configs_job_id ON scraper_configs (job_id);

-- 3. Create crawler_configs table
CREATE TABLE crawler_configs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id                  UUID NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,

    max_pages               INT             NOT NULL DEFAULT 100,
    max_depth               INT             NOT NULL DEFAULT 2,
    url_include_patterns    VARCHAR(2000),
    url_exclude_patterns    VARCHAR(2000),
    pagination_type         VARCHAR(30)     NOT NULL DEFAULT 'NONE',
    pagination_selector     VARCHAR(500),
    max_pagination_pages    INT             NOT NULL DEFAULT 50,
    follow_redirects        BOOLEAN         NOT NULL DEFAULT TRUE,
    respect_robots_txt      BOOLEAN         NOT NULL DEFAULT TRUE,
    sitemap_url             VARCHAR(2048),

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_crawler_pagination_type
        CHECK (pagination_type IN ('NONE','NUMBERED','INFINITE_SCROLL','LOAD_MORE','NEXT_BUTTON','CURSOR')),
    CONSTRAINT chk_crawler_max_depth
        CHECK (max_depth BETWEEN 0 AND 20)
);

CREATE INDEX idx_crawler_configs_job_id ON crawler_configs (job_id);

-- 4. Migrate existing jobs → scraper_configs (all existing jobs default to SCRAPER)
INSERT INTO scraper_configs (job_id, llm_extraction_prompt, single_page_mode, created_at, updated_at)
SELECT id, llm_extraction_prompt, FALSE, created_at, updated_at
FROM jobs
WHERE deleted = FALSE;

-- 5. Add index on job_type
CREATE INDEX idx_jobs_job_type ON jobs (job_type) WHERE deleted = FALSE;

-- 6. Auto-update updated_at on new tables
CREATE TRIGGER trg_scraper_configs_updated_at
    BEFORE UPDATE ON scraper_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_crawler_configs_updated_at
    BEFORE UPDATE ON crawler_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
