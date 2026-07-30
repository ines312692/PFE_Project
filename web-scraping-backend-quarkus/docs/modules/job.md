## Job Module

### Purpose

The Job module is the center of the platform. A job describes a target URL, an extraction mode, and either a scraper configuration (for a single page or a small, fixed set of pages) or a crawler configuration (for discovering and visiting many pages of a site). This module also covers job scheduling, site probing, webhook triggers, and webhook delivery tracking. Launching a job does not execute it inline. It is dispatched through Redis Streams and picked up by a worker, as described in [Distributed Job Processing](../architecture/distributed_jobs.md).

### Job Type and Status

1. `jobType` is either `SCRAPER`, for a fixed set of pages, or `CRAWLER`, for site wide discovery driven by the frontier described in the distributed processing document.
2. `extractionMode` controls how data is pulled out of a page. `CSS_SELECTOR` uses an extraction schema of named CSS selectors, see the [Schema Module](./schema.md). `AUTO` runs a generic extractor that captures the page URL, HTML and HTML length without a user supplied schema, and is what crawler jobs fall back to. Other modes exist for LLM based and regex based extraction.
3. `status` moves through `CREATED`, `QUEUED`, `RUNNING`, `PAUSED`, `WAITING_FOR_HUMAN` (when a CAPTCHA needs manual intervention), `COMPLETED`, `FAILED`, and `CANCELLED`. Only a subset of statuses are eligible for launch, see `JobEnqueueService`.

### Job Management

1. `POST /v1/jobs`. Creates a new job. Example body: `{"name": "My first job", "targetUrl": "https://example.com", "jobType": "SCRAPER", "priority": "NORMAL", "triggerType": "MANUAL", "extractionMode": "CSS_SELECTOR"}`. A job can also carry `browserConfig`, `retryPolicy`, `proxyConfig`, `extractionSchema`, `scraperConfig`, `crawlerConfig`, `cronExpression` and `projectId`.
2. `GET /v1/jobs`. Retrieves a paginated, filterable, sortable list of jobs. Query parameters: `page`, `size`, `search`, `status`, `priority`, `triggerType`, `jobType`, `createdFrom`, `createdTo`, `sortBy`, `sortDirection`.
3. `GET /v1/jobs/{id}`. Retrieves a single job by id.
4. `PATCH /v1/jobs/{id}`. Updates an existing job.
5. `DELETE /v1/jobs/{id}`. Deletes a job.
6. `POST /v1/jobs/{id}/duplicate`. Creates a copy of an existing job, in `CREATED` status.
7. `POST /v1/jobs/{id}/launch`. Enqueues the job for execution. Returns an error if the job is not currently in a launchable status.
8. `POST /v1/jobs/{id}/pause`. Pauses a running job.
9. `GET /v1/jobs/{id}/results`. Retrieves the latest scrape result for the job. See the [Scrape Result Module](./scraperesult.md).
10. `GET /v1/jobs/{id}/history`. Retrieves the full run history for the job.
11. `GET /v1/jobs/stats`. Retrieves aggregate job statistics, for example total, running, completed and failed counts.

### Webhook Credentials and Deliveries

1. `GET /v1/jobs/{id}/webhook`. Retrieves webhook information for a job, including the callback URL and whether a secret is set.
2. `POST /v1/jobs/{id}/webhook/rotate`. Rotates the webhook token and secret for a job.
3. `GET /v1/jobs/{id}/webhook/deliveries`. Retrieves the history of inbound webhook deliveries for a job.
4. `POST /v1/webhooks/jobs/{token}`. The public endpoint external systems call to trigger a job using its webhook token. The request is verified using an HMAC signature computed from the webhook secret.

### Job Scheduling

1. `GET /v1/jobs/schedule/upcoming`. Retrieves upcoming scheduled runs within a time range. Query parameters: `from`, `to` (ISO 8601).
2. `GET /v1/jobs/schedule/preview`. Previews the next fire times for a cron expression, without saving anything. Query parameters: `cron`, `count`.

Scheduling itself is implemented with Quartz. A job with a `cronExpression` is registered with the scheduler when it is created, and the scheduler is responsible for enqueueing it at the right time, using the same `JobEnqueueService` path as a manual launch.

### Site Probing

1. `POST /v1/jobs/probe`. Probes a target URL before creating a job, to check reachability and detect signals such as an SPA shell, a hard anti bot challenge page, or an outright blocked response. Example body: `{"url": "https://example.com"}`.

### Action Planner

1. `POST /v1/jobs/action-planner`. Given a natural language instruction and a target page, asks an LLM to propose a sequence of browser actions, such as clicking a button or filling a field, that a scraper can replay before extracting data. This is used to configure interactive scrape flows, for example dismissing a cookie banner or logging in.

### Related Modules

1. [Distributed Job Processing](../architecture/distributed_jobs.md) describes what happens after a job is launched.
2. [Scraper Module](./scraper.md) describes the engine that actually visits pages and extracts data.
3. [Proxy Module](./proxy.md) and [Schema Module](./schema.md) describe two of the configuration pieces a job can reference.
4. [Notification Module](./notification.md) describes how a job owner is informed of completion, failure, or a CAPTCHA that needs manual help.
