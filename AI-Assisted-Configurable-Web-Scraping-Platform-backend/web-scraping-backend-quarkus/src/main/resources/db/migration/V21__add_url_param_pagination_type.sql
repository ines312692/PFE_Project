-- ════════════════════════════════════════════════════════════════════
-- V21: Add URL_PARAM to pagination_type CHECK constraints
-- ════════════════════════════════════════════════════════════════════
-- Allows pagination strategy via incrementing a query parameter
-- (e.g. ?p=1, ?page=2) — ideal for SPA sites like HuggingFace.
-- ════════════════════════════════════════════════════════════════════

-- ─── scraper_configs ────────────────────────────────────────────────
ALTER TABLE scraper_configs
    DROP CONSTRAINT IF EXISTS chk_scraper_configs_pagination_type;

ALTER TABLE scraper_configs
    ADD CONSTRAINT chk_scraper_configs_pagination_type
    CHECK (pagination_type IN (
        'NONE',
        'NUMBERED',
        'INFINITE_SCROLL',
        'LOAD_MORE',
        'NEXT_BUTTON',
        'CURSOR',
        'URL_PARAM'
    ));

-- ─── crawler_configs ────────────────────────────────────────────────
ALTER TABLE crawler_configs
    DROP CONSTRAINT IF EXISTS chk_crawler_configs_pagination_type;

ALTER TABLE crawler_configs
    ADD CONSTRAINT chk_crawler_configs_pagination_type
    CHECK (pagination_type IN (
        'NONE',
        'NUMBERED',
        'INFINITE_SCROLL',
        'LOAD_MORE',
        'NEXT_BUTTON',
        'CURSOR',
        'URL_PARAM'
    ));
