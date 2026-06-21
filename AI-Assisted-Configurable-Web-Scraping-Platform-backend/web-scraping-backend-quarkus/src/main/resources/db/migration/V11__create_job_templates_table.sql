CREATE TABLE job_templates (
                               id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                               name        VARCHAR(255) NOT NULL,
                               description TEXT         NOT NULL,
                               category    VARCHAR(50)  NOT NULL,
                               job_type    VARCHAR(50)  NOT NULL,
                               tags        JSONB        NOT NULL DEFAULT '[]',
                               popularity  INTEGER      NOT NULL DEFAULT 0,
                               config      JSONB        NOT NULL DEFAULT '{}',
                               created_at  TIMESTAMP    NOT NULL DEFAULT now(),
                               updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_templates_category   ON job_templates (category);
CREATE INDEX idx_templates_popularity ON job_templates (popularity DESC);

INSERT INTO job_templates (name, description, category, job_type, tags, popularity, config) VALUES

                                                                                                ('Amazon Product Scraper',
                                                                                                 'Extract product details, prices, ratings and reviews from Amazon listings.',
                                                                                                 'E_COMMERCE', 'SCRAPER',
                                                                                                 '["amazon","products","prices","reviews"]', 92,
                                                                                                 '{"scraperConfig":{"targetUrl":"https://www.amazon.com/s?k=","extractionMode":"LLM","paginationType":"NEXT_PAGE"},"browserConfig":{"javascript":true,"headless":true},"retryPolicy":{"maxRetries":3,"retryDelay":2000}}'),

                                                                                                ('Shopify Store Monitor',
                                                                                                 'Monitor product availability and price changes across any Shopify-powered store.',
                                                                                                 'E_COMMERCE', 'CRAWLER',
                                                                                                 '["shopify","monitor","prices","stock"]', 78,
                                                                                                 '{"crawlerConfig":{"startUrl":"https://","maxDepth":3,"urlPattern":"/products/"},"browserConfig":{"javascript":true,"headless":true},"retryPolicy":{"maxRetries":2,"retryDelay":3000}}'),

                                                                                                ('News Article Extractor',
                                                                                                 'Extract full article content, author, publish date and metadata from news websites.',
                                                                                                 'NEWS', 'SCRAPER',
                                                                                                 '["news","articles","content","metadata"]', 85,
                                                                                                 '{"scraperConfig":{"targetUrl":"","extractionMode":"LLM","paginationType":"NONE"},"browserConfig":{"javascript":false,"headless":true},"retryPolicy":{"maxRetries":2,"retryDelay":1000}}'),

                                                                                                ('News Site Crawler',
                                                                                                 'Crawl an entire news portal to collect all article URLs and basic metadata.',
                                                                                                 'NEWS', 'CRAWLER',
                                                                                                 '["news","crawl","sitemap","bulk"]', 70,
                                                                                                 '{"crawlerConfig":{"startUrl":"","maxDepth":4,"urlPattern":"/article/"},"browserConfig":{"javascript":false,"headless":true},"retryPolicy":{"maxRetries":2,"retryDelay":1500}}'),

                                                                                                ('Property Listings Scraper',
                                                                                                 'Collect property details, prices, surface area, location and contact info from listing sites.',
                                                                                                 'REAL_ESTATE', 'SCRAPER',
                                                                                                 '["real-estate","listings","prices","location"]', 80,
                                                                                                 '{"scraperConfig":{"targetUrl":"","extractionMode":"LLM","paginationType":"NEXT_PAGE"},"browserConfig":{"javascript":true,"headless":true},"retryPolicy":{"maxRetries":3,"retryDelay":2000}}'),

                                                                                                ('LinkedIn Jobs Crawler',
                                                                                                 'Crawl LinkedIn job listings to collect titles, companies, locations and requirements.',
                                                                                                 'JOB_BOARDS', 'CRAWLER',
                                                                                                 '["linkedin","jobs","recruitment","companies"]', 88,
                                                                                                 '{"crawlerConfig":{"startUrl":"https://www.linkedin.com/jobs/search/?keywords=","maxDepth":2,"urlPattern":"/jobs/view/"},"browserConfig":{"javascript":true,"headless":true},"retryPolicy":{"maxRetries":3,"retryDelay":3000}}'),

                                                                                                ('Indeed Job Scraper',
                                                                                                 'Extract job postings including salary, requirements and company ratings from Indeed.',
                                                                                                 'JOB_BOARDS', 'SCRAPER',
                                                                                                 '["indeed","jobs","salary","requirements"]', 75,
                                                                                                 '{"scraperConfig":{"targetUrl":"https://indeed.com/jobs?q=","extractionMode":"LLM","paginationType":"NEXT_PAGE"},"browserConfig":{"javascript":true,"headless":true},"retryPolicy":{"maxRetries":2,"retryDelay":2000}}'),

                                                                                                ('Stock Market Data Scraper',
                                                                                                 'Collect real-time and historical stock prices, volumes and financial indicators.',
                                                                                                 'FINANCE', 'SCRAPER',
                                                                                                 '["stocks","finance","prices","market"]', 83,
                                                                                                 '{"scraperConfig":{"targetUrl":"","extractionMode":"CSS","paginationType":"NONE"},"browserConfig":{"javascript":true,"headless":true},"retryPolicy":{"maxRetries":5,"retryDelay":1000}}'),

                                                                                                ('Reddit Thread Scraper',
                                                                                                 'Extract posts, comments, scores and metadata from Reddit threads and subreddits.',
                                                                                                 'SOCIAL', 'SCRAPER',
                                                                                                 '["reddit","posts","comments","community"]', 71,
                                                                                                 '{"scraperConfig":{"targetUrl":"https://www.reddit.com/r/","extractionMode":"LLM","paginationType":"SCROLL"},"browserConfig":{"javascript":true,"headless":true},"retryPolicy":{"maxRetries":2,"retryDelay":1500}}');