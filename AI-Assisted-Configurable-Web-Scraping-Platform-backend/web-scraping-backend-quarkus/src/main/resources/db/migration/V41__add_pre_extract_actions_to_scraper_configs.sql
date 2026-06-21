-- ═══════════════════════════════════════════════════════════════════
-- V41__add_pre_extract_actions_to_scraper_configs.sql
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE scraper_configs
    ADD COLUMN IF NOT EXISTS pre_extract_actions jsonb NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN scraper_configs.pre_extract_actions
    IS 'Ordered list of interactive PageAction objects replayed before extraction. JSONB array, defaults to [].';