## Application Overview

### Purpose

The AI Assisted Configurable Web Scraping Platform is a Quarkus and Java backend that lets a user configure, schedule, run and monitor web scraping and web crawling jobs. It combines a classic CRUD backend (projects, jobs, users) with a distributed scraping engine built on Playwright, and adds AI assistance for parts of the workflow that are traditionally manual, such as building an extraction schema or repairing a broken CSS selector.

### Technology Stack

1. Runtime and framework: Java 21, Quarkus 3.
2. Persistence: PostgreSQL in development and production, H2 in tests, accessed through Hibernate ORM with Panache style repositories, with Flyway migrations.
3. Distributed coordination: Redis, used both as a message queue (Redis Streams with consumer groups) and as shared state storage (sets, hashes, counters) for the distributed crawler.
4. Browser automation: Playwright, with a Selenium based fallback path in parts of the antibot tooling.
5. Scheduling: Quartz, for cron based job scheduling.
6. Identity: Keycloak, through a Backend For Frontend (BFF) pattern with encrypted, HTTP only cookies.
7. AI: Groq, accessed through LangChain4j, used for extraction schema assistance, CAPTCHA fallback solving, and ETL text enrichment steps.
8. Testing: JUnit 5, Quarkus test support, RestAssured, Mockito, and Awaitility for asynchronous flows.

### High Level Building Blocks

The backend is organized as a set of self contained feature modules under `src/main/java/com/elyadata/feature`, each following the same internal layering.

1. `application`: REST controllers, request and response DTOs, and mappers between DTOs and domain objects.
2. `domain`: domain models, domain services, and ports (interfaces) that describe what the domain needs from the outside world.
3. `infrastructure`: adapters that implement the domain ports, JPA entities, repositories, and anything that talks to an external system (Redis, Keycloak, a browser engine, an LLM provider).

This is a hexagonal style layering. Domain services depend on ports, and adapters implement those ports, which keeps business logic independent from persistence and transport concerns.

### Feature Modules

1. `auth`: login, logout and session handling through the Keycloak BFF flow. See [Auth Module](../modules/auth.md).
2. `user`: user administration and personal account management. See [User Module](../modules/user.md).
3. `project`: grouping of jobs into projects, membership and invitations. See [Project Module](../modules/project.md).
4. `job`: job definitions, scheduling, webhooks, site probing, and the Redis Streams based dispatch and execution pipeline. See [Job Module](../modules/job.md) and [Distributed Job Processing](./distributed_jobs.md).
5. `scraper`: the scraping and crawling engine itself, browser automation, extractors, antibot and CAPTCHA handling, resilience. See [Scraper Module](../modules/scraper.md).
6. `scraperesult`: storage and retrieval of the data produced by a scrape or crawl. See [Scrape Result Module](../modules/scraperesult.md).
7. `proxy`: proxy pool management and rotation used by the scraper. See [Proxy Module](../modules/proxy.md).
8. `schema`: extraction schema definition and AI assisted schema building. See [Schema Module](../modules/schema.md).
9. `etl`: post scrape data cleaning, normalization, enrichment and export. See [ETL Module](../modules/etl.md).
10. `template`: reusable job configuration templates. See [Template Module](../modules/template.md).
11. `visualselector`: backend support for a visual, click to select CSS selector builder. See [Visual Selector Module](../modules/visualselector.md).
12. `audit`: audit logging of sensitive operations. See [Audit Module](../modules/audit.md).
13. `notification`: in app user notifications. See [Notification Module](../modules/notification.md).
14. `dataversion`: versioning of the data produced over successive runs of a job. See [Data Version Module](../modules/dataversion.md).
15. `monitoring` and `llmusage`: operational metrics, job performance, and LLM usage and cost tracking. See [Monitoring Module](../modules/monitoring.md).

### Request Flow, In Short

1. A client authenticates through the `auth` module and receives encrypted session cookies.
2. The client creates a project and a job, describing the target URL, the extraction mode, and either a scraper configuration or a crawler configuration.
3. Launching a job does not run it inline. It enqueues a message on a Redis stream, and a pool of workers picks it up, so job execution is decoupled from the HTTP request and can be scaled across multiple backend instances. See [Distributed Job Processing](./distributed_jobs.md) for the full mechanism.
4. A worker runs the scraping or crawling pipeline, using Playwright, the configured extractor, and the antibot and proxy subsystems as needed.
5. The result is persisted through the `scraperesult` module, the job status is updated, and, on success, an ETL message is dispatched for further cleaning and enrichment.
6. The user is notified in app, and can review results, run history, and job statistics through the REST API.

### Where To Look Next

1. For the distributed execution pipeline in detail, read [Distributed Job Processing](./distributed_jobs.md).
2. For how to run the application locally, and which environment variables matter, read [Configuration](../operations/configuration.md).
3. For how the test suite is organized, including the real end to end tests of the distributed pipeline, read [Testing](../operations/testing.md).
4. For visual diagrams (C4 and sequence diagrams in PlantUML format), see the [diagrams](../diagrams) folder.
