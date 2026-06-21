-- ============================================================
-- V6__add_logs_to_scrape_results.sql
-- Adds a logs column to store scraping pipeline logs
-- ============================================================

ALTER TABLE scrape_results
    ADD COLUMN logs JSONB NOT NULL DEFAULT '[]';