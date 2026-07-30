## Template Module

### Purpose

The Template module offers a read only catalogue of pre built, reusable job configurations, for example a typical e commerce product page setup, so a user can start from a known good configuration instead of building a scrape job from scratch.

### Endpoints

1. `GET /v1/templates`. Lists all available templates.
2. `GET /v1/templates/{id}`. Retrieves a single template by id.

Only read operations are exposed. There is no endpoint to create, update or delete a template through the API.

### Template Fields

1. `name` and `description`. Human readable identification of the template.
2. `category`. One of e commerce, news, real estate, job boards, finance, or social.
3. `jobType`. The job type the template is meant for, scraper or crawler.
4. `tags`. Free form labels used for search and filtering.
5. `popularity`. A ranking signal, for example based on usage.
6. `config`. The actual configuration blob to copy into a new job when the template is applied.

### Related Modules

1. [Job Module](./job.md) is where a template's configuration ends up once applied to a new job.
