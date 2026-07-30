## Monitoring Module

### Purpose

This area covers three related concerns. System health reports the overall state of the backend, its Redis Streams backlog, worker pools, JVM, HTTP traffic, and aggregate LLM and CAPTCHA counters. Performance monitoring reports scraping throughput and success rate over time, computed directly from stored scrape results. LLM usage tracking reports cost, latency and token consumption for every LLM call made anywhere in the application, backed by a shared observability layer that every LLM consuming module writes to.

### System Health

1. `GET /v1/monitoring/system-health`. Returns an overall status of healthy, degraded, or critical, derived from the dead letter queue depth, JVM heap usage, HTTP error rate, and worker pool utilization, plus the underlying figures themselves: stream backlog, worker pool occupancy, JVM heap and thread counts, HTTP request and error counts, aggregate LLM call and token counts, and CAPTCHA solving attempt and success counts.

### Scraping Performance

All of the following accept a `range` query parameter, one of `24h`, `7d`, or `30d`, and scope results to the current user unless the caller is an administrator.

1. `GET /v1/monitoring/performance/summary`. Aggregate throughput in pages and records per minute, average crawl speed, average and 95th percentile duration, success rate, and run counts by outcome.
2. `GET /v1/monitoring/performance/timeseries`. A bucketed time series of pages, records, runs and pages per minute, comparing the current window to the previous one.
3. `GET /v1/monitoring/performance/by-engine`. A breakdown of run count, pages, average speed and success rate per scraping engine.
4. `GET /v1/monitoring/performance/runs`. The most recent individual job runs, with engine, status, pages, records, duration and completion time.

These figures are computed on read from the stored scrape results, there is no separate events table dedicated to performance history.

### LLM Usage

All of the following accept optional `projectId`, `from` and `to` filters, defaulting to a trailing thirty day window when a date range is not given.

1. `GET /v1/llm-usage/summary`. Total calls, prompt, completion and total tokens, an estimated cost, average latency, success rate, and the count of rate limited calls.
2. `GET /v1/llm-usage/by-feature`. A breakdown per feature, for example extraction, schema assist, schema validation, selector healing, pagination detection, action planning, action resolution, or vision based CAPTCHA solving.
3. `GET /v1/llm-usage/by-model`. A breakdown per underlying model.
4. `GET /v1/llm-usage/by-project`. A breakdown per project.
5. `GET /v1/llm-usage/daily`. A daily time series of calls, tokens and failures.
6. `GET /v1/llm-usage/recent`. The most recent individual LLM call records, including provider, model, feature, outcome, related job and project, token counts, latency, cost and any error message.

### LLM Observability Infrastructure

Every LLM call made anywhere in the application, whether for extraction, schema assistance, selector healing, pagination detection, action planning, or vision based CAPTCHA solving, is timed, tagged with the feature it belongs to, and recorded through one shared recorder. Recording happens in its own database transaction, separate from the caller's business transaction, so a failure to record a call never rolls back the work that triggered it. The same counters feed both the LLM usage endpoints described above and the LLM section of the system health endpoint.

### Related Modules

Every module that makes LLM calls, including [Schema Module](./schema.md), [Scraper Module](./scraper.md) and [ETL Module](./etl.md), is a source of the data shown here.
