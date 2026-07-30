## Data Version Module

### Purpose

The Data Version module tracks the history of a scrape result as it passes through ETL transformations. Every ETL run that is saved produces a new, immutable version, with a link back to its parent, so a user can browse history, inspect any past version, or roll back to an earlier one. A rollback itself creates a new version rather than rewriting history.

### Endpoints

1. `GET /v1/scrape-results/{scrapeResultId}/versions`. Lists the version history for a scrape result, as summaries.
2. `GET /v1/scrape-results/{scrapeResultId}/versions/{versionNumber}`. Retrieves full detail for one version, including its records.
3. `POST /v1/scrape-results/{scrapeResultId}/versions/{versionNumber}/rollback`. Rolls back to a target version, by creating a new version whose content mirrors that target.

### Version Fields

1. `versionNumber`. An incrementing number, starting at one for the first saved ETL run.
2. `parentVersionId`. A link to the version this one was derived from.
3. `records`. The record set at this version.
4. `appliedSteps`. The ETL steps that were applied to produce this version.
5. `recordCount`. The number of records in this version.
6. `label`. A short description, defaulted to a summary of the ETL run, or set explicitly, for example when rolling back.

Version zero is a synthetic entry that always represents the raw, untouched scrape result. It is never actually stored. It is reconstructed on read from the scrape result itself, so that version history always has a stable starting point even before any ETL run has been saved.

### Related Modules

1. [ETL Module](./etl.md) is what produces a new version when a transformation is saved.
2. [Scrape Result Module](./scraperesult.md) is the source of the raw, version zero content.
