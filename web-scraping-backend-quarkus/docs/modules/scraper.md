## Scraper Module

### Purpose

The Scraper module is the engine that actually visits pages, handles anti bot defenses, and extracts data. It has no REST controller of its own. It is invoked by the Job module, through `ScrapeEngine`, once a job has been picked up by a worker as described in [Distributed Job Processing](../architecture/distributed_jobs.md).

### Browser Automation

The platform drives a real browser through Playwright, with a Selenium based fallback path used by some of the anti bot tooling.

1. Headless and headful modes. Headless is used in production and in tests. Headful mode is available for local debugging.
2. Proxy support. A browser instance can be configured to route its traffic through a proxy from the [Proxy Module](./proxy.md).
3. Stealth mode, under `scraper/browser/stealth`. Applies a consistency profile that spoofs the user agent, randomizes canvas and WebGL fingerprints, and adjusts fonts and plugins so the browser looks like an ordinary consumer browser rather than an automated one.
4. Human like interaction, under `scraper/browser/humanizer`. Generates realistic mouse movement paths, human like typing and scrolling timing, so interactions do not look mechanically uniform.
5. Session handling, under `scraper/browser/session`. Stores and restores browser storage state, so a job can resume with an existing logged in session instead of authenticating on every run.
6. Login strategies, under `scraper/browser/login`. Encapsulates different ways to authenticate on a target site before scraping, selected through `LoginStrategyRegistry`.

### Crawling

The crawler discovers pages to visit and feeds them to the extraction step. Two implementations exist. `UrlFrontier` is a simple, single process frontier used by the legacy, non distributed crawl path. `DistributedCrawlFrontier` is the Redis backed, multi worker frontier described in [Distributed Job Processing](../architecture/distributed_jobs.md).

1. Link extraction and filtering, under `scraper/crawler/url`. `LinkHarvester` extracts links from a page, `UrlFilter` applies include and exclude patterns, and `UrlNormalizer` canonicalizes URLs so equivalent links are not treated as distinct.
2. Pagination, under `scraper/crawler/pagination`. Detects and follows several pagination styles: numbered pages, a next button, a URL parameter, infinite scroll, and a load more button, each implemented as its own strategy behind `PaginationStrategyRegistry`.
3. Robots and sitemaps, under `scraper/crawler/robots` and `scraper/crawler/sitemap`. `RobotsTxtGate` checks `robots.txt` before visiting a URL, when the job's crawler configuration asks to respect it. The sitemap package can seed the frontier from a site's sitemap.
4. File discovery, under `scraper/crawler/discovery`. `SiteFileDiscoverer` probes for common files such as `robots.txt`, `sitemap.xml`, a manifest, and About or Contact pages, and reports them as `DiscoveredFile` entries alongside the extracted records.

### Data Extraction

Extraction turns a page snapshot into structured records, dispatched by `ExtractorRegistry` according to the job's extraction mode.

1. `CssExtractor`. Extracts data using named CSS selectors from an extraction schema, see the [Schema Module](./schema.md).
2. `XPathExtractor`. Extracts data using XPath expressions.
3. `RegexExtractor`. Extracts data using regular expressions applied to the page text.
4. `LlmExtractor`. Extracts data by asking an LLM to read the page and return structured output according to a prompt.
5. `AutoExtractor`. A generic fallback that captures the page URL, HTML and HTML length without any user supplied schema. This is what crawler jobs use by default, since a single schema rarely fits every page type discovered during a crawl.

### Anti-Bot and CAPTCHA Handling

Anti bot handling is organized under `scraper/antibot`.

1. Site protection detection, under `scraper/site/detector`. Detects known protection systems, for example Cloudflare, from response headers, status codes and body markers, and produces a `ScrapingDifficultyScore` used to decide whether extra measures such as stealth mode or proxy rotation are warranted.
2. DNS history, under `scraper/site/dns`. Looks up historical DNS records to find a site's origin IP address behind a CDN, using a registry of CDN IP ranges and a CIDR matcher, sometimes making direct access possible even when the CDN in front would otherwise block the scraper.
3. CAPTCHA detection and solving, under `scraper/antibot/captcha`. `LiveCaptchaDetector` inspects the live page for a CAPTCHA challenge, with an LLM based fallback detector when the live detector is inconclusive. Dedicated solvers exist per provider, including reCAPTCHA, hCaptcha, FunCaptcha, GeeTest and Cloudflare Turnstile, plus an LLM vision based solver and a rotate style puzzle solver, all registered in `CaptchaSolverRegistry` and coordinated by `CaptchaHandler`.
4. Human intervention, under `scraper/antibot/captcha/intervention`. When automated solving is not possible or not enabled, the job status moves to `WAITING_FOR_HUMAN`, a `CAPTCHA_INTERVENTION_REQUIRED` notification is sent, and a human operator can submit the solved token through a dedicated endpoint to let the job resume.
5. Result caching, under `scraper/antibot/captcha/cache`. `CaptchaTokenCache` avoids resolving the same challenge twice within a short time window.

### Resilience

Under `scraper/resilience`.

1. `DomainCircuitBreaker`, registered per domain in `CircuitBreakerRegistry`. Opens when a domain starts failing repeatedly, so the crawler stops hammering a site that is actively blocking it, and closes again after a cool down period.
2. `RetryExecutor`. Retries a failed navigation or extraction according to the job's retry policy, with exponential backoff.

### Security

Under `scraper/security`. `SsrfGuard` evaluates every target URL before it is fetched, blocking requests to internal or otherwise dangerous addresses such as loopback and private network ranges, cloud metadata endpoints, and disallowed ports. In test environments this guard can be configured to allow private networks, so tests can target a local HTTP server.

### Related Modules

1. [Job Module](./job.md) describes how a job configuration reaches this engine.
2. [Distributed Job Processing](../architecture/distributed_jobs.md) describes the distributed crawl coordination that sits on top of the crawler described here.
3. [Proxy Module](./proxy.md) describes where the proxies used by the browser come from.
4. [Schema Module](./schema.md) describes how a CSS or LLM based extraction schema is built and repaired.
