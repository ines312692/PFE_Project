ALTER TABLE scrape_results
    ADD COLUMN discovered_files JSONB DEFAULT '[]'::jsonb;

COMMENT ON COLUMN scrape_results.discovered_files
    IS 'Metadata files found during crawl (robots.txt, sitemap, about, contact, etc.)';