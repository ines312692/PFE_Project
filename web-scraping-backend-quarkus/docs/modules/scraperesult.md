## Scrape Result Module

### Purpose

This module persists and serves the output of a job run. Every time a job completes, whether successfully or not, a scrape result is stored with the extracted records, any errors and log lines, and run metadata such as duration and the number of pages crawled.

### Endpoints

1. `GET /v1/scrape-results/job/{jobId}/latest`. Retrieves the most recent scrape result for a job.
2. `GET /v1/scrape-results/job/{jobId}`. Retrieves the full result history for a job, one entry per run.
3. `GET /v1/scrape-results/{id}`. Retrieves a single result by its own id.

### Result Fields

1. `status`. A free form status string for the run, for example completed or failed.
2. `records`. The extracted structured data, as a list of field maps.
3. `errors`. Error messages captured during the run.
4. `logs`. Log lines captured during the run, useful for troubleshooting a failed or partial scrape.
5. `pagesCrawled`. The number of pages visited during the run.
6. `durationMs`. Total run duration in milliseconds.
7. `engineUsed`. Which browser engine handled the run.
8. `scrapedAt`. When the run completed.
9. `discoveredFiles`. Non record files found while crawling, for example a PDF or an image, each with its own URL, file type, HTTP status and content preview.

A single job can accumulate many scrape results over time, one per run, which is what the history endpoint exposes.

### Related Modules

1. [Job Module](./job.md) exposes the latest result and the history for a job through its own endpoints, backed by this module.
2. [ETL Module](./etl.md) reads a scrape result's records as its input.
3. [Data Version Module](./dataversion.md) tracks the successive transformations applied to a scrape result's records.
