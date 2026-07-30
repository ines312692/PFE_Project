## Configuration

### Profiles

The application configuration lives in `src/main/resources/application.yml` and is split across three Quarkus profiles.

1. `%dev`. Used for local development. Connects to Redis and PostgreSQL on localhost, runs Flyway migrations at startup, and enables verbose logging for the scraper and stream packages.
2. `%test`. Used by the automated test suite. Uses an in memory H2 database recreated on every run, disables Flyway, disables OIDC, and disables the Redis Streams workers so that ordinary unit and controller tests are not affected by background polling. See [Testing](./testing.md) for how the distributed pipeline is still exercised for real in a dedicated set of tests.
3. `%prod`. Used in a deployed environment. Every connection detail is read from an environment variable, with no local default, so the application will not start against the wrong database or Redis instance by accident.

### Key Environment Variables

1. `APP_PUBLIC_BASE_URL`. The externally visible base URL of the backend, used to build links such as webhook callback URLs.
2. `CORS_ALLOWED_ORIGINS`. The frontend origin allowed to call the API.
3. `SESSION_ENCRYPTION_KEY`. The key used to encrypt session data, including stored browser storage state, at rest.
4. `KEYCLOAK_URL` and `KEYCLOAK_REALM`. Where Keycloak lives and which realm to use for authentication.
5. `BFF_CLIENT_ID` and `BFF_CLIENT_SECRET`. The confidential client credentials the backend uses to talk to Keycloak on behalf of the frontend.
6. `BFF_CALLBACK_URI` and `FRONTEND_URL`. Where Keycloak should redirect after login, and where the frontend lives.
7. `BFF_COOKIE_SECURE`. Whether authentication cookies require HTTPS. This should be true in production.
8. `REDIS_URL` and `REDIS_POOL`. The Redis connection string used in production, and the connection pool size, which should be at least as large as the configured stream worker concurrency.
9. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. PostgreSQL connection details used in production.
10. `SCRAPER_MAX_ASYNC`. The default concurrency for the scrape worker pool and, by extension, for several stream worker pools that reuse the same default.
11. `SCRAPER_STREAM_ENABLED`. Turns the Redis Streams dispatch and worker pipeline on or off. See [Distributed Job Processing](../architecture/distributed_jobs.md).
12. `SCRAPER_DISTRIBUTED_CRAWL`. Turns distributed crawl coordination on or off for crawler type jobs.
13. `SCRAPER_SSRF_ENABLED` and `SCRAPER_SSRF_ALLOW_PRIVATE`. Control the SSRF guard described in the [Scraper Module](../modules/scraper.md). Private networks are only allowed in the test profile.
14. `SCRAPER_CF_DOMAINS`. A list of domains known to sit behind Cloudflare, used as a hint by the anti bot detection logic.
15. `PROXY_MAX_ROTATIONS`, `PROXY_ROTATION_BUDGET_MS`, `PROXY_LEASE_MINUTES`, and the various `PROXY_HEALTH_*` and `PROXY_CF_*` settings. Control proxy rotation and health checking described in the [Proxy Module](../modules/proxy.md).
16. `TWO_CAPTCHA_API_KEY`. Credentials for the external CAPTCHA solving provider used by the [Scraper Module](../modules/scraper.md).
17. `GROQ_API_KEY` and `GROQ_MODEL`. Credentials and default model for the LLM provider used across the [Schema Module](../modules/schema.md), the [ETL Module](../modules/etl.md), and CAPTCHA fallback solving.

Consult `application.yml` directly for the full, authoritative list, since it is kept up to date as the application evolves and this document only summarizes the variables most relevant to running and operating the platform.
