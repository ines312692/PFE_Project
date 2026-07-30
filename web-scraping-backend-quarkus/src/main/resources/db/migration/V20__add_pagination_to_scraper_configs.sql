-- ═══════════════════════════════════════════════════════════════════
-- V20__add_pagination_to_scraper_configs.sql
--
-- Adds pagination support to the scraper_configs table.
-- crawler_configs already has these columns (since V1/V7).
-- This brings scraper_configs to feature parity.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE scraper_configs
    ADD COLUMN IF NOT EXISTS pagination_type       VARCHAR(30)  NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS pagination_selector   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS max_pagination_pages  INT          NOT NULL DEFAULT 10;

-- Check constraint: same allowed values as crawler_configs and jobs table (V1)
ALTER TABLE scraper_configs
    ADD CONSTRAINT chk_scraper_configs_pagination_type
        CHECK (pagination_type IN ('NONE', 'NUMBERED', 'INFINITE_SCROLL', 'LOAD_MORE', 'NEXT_BUTTON', 'CURSOR'));

COMMENT ON COLUMN scraper_configs.pagination_type      IS 'Pagination strategy: NONE | NUMBERED | INFINITE_SCROLL | LOAD_MORE | NEXT_BUTTON | CURSOR';
COMMENT ON COLUMN scraper_configs.pagination_selector  IS 'Optional CSS selector for the pagination element. Null = auto-detect.';
COMMENT ON COLUMN scraper_configs.max_pagination_pages IS 'Max number of pagination pages to follow per URL (default 10).';
