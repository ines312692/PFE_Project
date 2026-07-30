
CREATE INDEX IF NOT EXISTS idx_scrape_results_scraped_at
    ON scrape_results (scraped_at DESC);

CREATE INDEX IF NOT EXISTS idx_scrape_results_job_scraped
    ON scrape_results (job_id, scraped_at DESC);