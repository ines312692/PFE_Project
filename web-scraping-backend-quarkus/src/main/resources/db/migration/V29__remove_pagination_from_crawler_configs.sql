ALTER TABLE crawler_configs DROP CONSTRAINT IF EXISTS chk_crawler_pagination_type;
ALTER TABLE crawler_configs DROP COLUMN IF EXISTS pagination_type;
ALTER TABLE crawler_configs DROP COLUMN IF EXISTS pagination_selector;
ALTER TABLE crawler_configs DROP COLUMN IF EXISTS max_pagination_pages;