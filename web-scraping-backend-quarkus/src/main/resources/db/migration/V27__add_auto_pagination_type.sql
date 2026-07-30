-- ─────────────────────────────────────────────────────────────────────
-- V27 — Add AUTO to allowed pagination_type values
--
-- The pagination auto-detection feature introduces a new value 'AUTO'
-- which lets the platform detect the best pagination strategy at runtime
-- by inspecting the target page. The existing CHECK constraints on
-- scraper_configs and crawler_configs reject this value, so we drop and
-- recreate them with the extended list.
-- ─────────────────────────────────────────────────────────────────────

ALTER TABLE scraper_configs
    DROP CONSTRAINT IF EXISTS chk_scraper_configs_pagination_type;

ALTER TABLE scraper_configs
    ADD CONSTRAINT chk_scraper_configs_pagination_type
        CHECK (pagination_type IN (
            'NONE',
            'AUTO',
            'NUMBERED',
            'INFINITE_SCROLL',
            'LOAD_MORE',
            'NEXT_BUTTON',
            'URL_PARAM',
            'CURSOR'
        ));

ALTER TABLE crawler_configs
    DROP CONSTRAINT IF EXISTS chk_crawler_configs_pagination_type;

ALTER TABLE crawler_configs
    ADD CONSTRAINT chk_crawler_configs_pagination_type
        CHECK (pagination_type IN (
            'NONE',
            'AUTO',
            'NUMBERED',
            'INFINITE_SCROLL',
            'LOAD_MORE',
            'NEXT_BUTTON',
            'URL_PARAM',
            'CURSOR'
        ));
