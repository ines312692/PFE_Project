# Job Module

The Job module is responsible for managing, scheduling, and executing web scraping jobs. It also provides features for site probing and webhook integration.

## Job Management

The Job Management API allows you to create, retrieve, update, and delete jobs.

### Endpoints

#### Create a new job

- **`POST /v1/jobs`**

  Creates a new web scraping job.

  **Request Body:**

  ```json
  {
    "name": "My first job",
    "url": "https://example.com",
    "cron": "0 0 * * *",
    "enabled": true,
    "scraper": "default",
    "params": {}
  }
  ```

#### Search for jobs

- **`GET /v1/jobs`**

  Retrieves a paginated list of jobs.

  **Query Parameters:**

  - `page` (optional): The page number to retrieve.
  - `size` (optional): The number of jobs per page.
  - `search` (optional): A search term to filter jobs by name.

#### Retrieve a job by its ID

- **`GET /v1/jobs/{id}`**

  Retrieves a single job by its unique identifier.

#### Update a job

- **`PUT /v1/jobs/{id}`**

  Updates an existing job.

  **Request Body:**

  ```json
  {
    "name": "My updated job",
    "url": "https://example.com",
    "cron": "0 0 * * *",
    "enabled": true,
    "scraper": "default",
    "params": {}
  }
  ```

#### Delete a job

- **`DELETE /v1/jobs/{id}`**

  Deletes a job.

#### Retrieve the scrape results for a job

- **`GET /v1/jobs/{id}/results`**

  Retrieves the scrape results for a specific job.

#### Run a job

- **`POST /v1/jobs/{id}/run`**

  Manually triggers a job to run immediately.

#### Stop a job

- **`POST /v1/jobs/{id}/stop`**

  Stops a running job.

#### Retrieve job statistics

- **`GET /v1/jobs/stats`**

  Retrieves statistics about the jobs, such as the total number of jobs, the number of running jobs, etc.

#### Webhook Credentials

- **`GET /v1/jobs/{id}/webhook/credentials`**: Retrieves webhook credentials for a job.
- **`POST /v1/jobs/{id}/webhook/credentials`**: Generates new webhook credentials for a job.
- **`GET /v1/jobs/{id}/webhook/info`**: Retrieves webhook information for a job.
- **`GET /v1/jobs/{id}/webhook/deliveries`**: Retrieves webhook deliveries for a job.
- **`POST /v1/jobs/{id}/webhook/ping`**: Pings a webhook for a job.
- **`POST /v1/jobs/{id}/webhook/redeliver`**: Redelivers a webhook for a job.

## Job Scheduling

The Job Scheduling API allows you to manage the scheduling of jobs.

### Endpoints

#### Retrieve upcoming job runs

- **`GET /v1/jobs/schedule/upcoming`**

  Retrieves a list of upcoming job runs within a specified time range.

  **Query Parameters:**

  - `from` (optional): The start of the time range in ISO 8601 format.
  - `to` (optional): The end of the time range in ISO 8601 format.

#### Preview the next fire times for a cron expression

- **`GET /v1/jobs/schedule/preview`**

  Previews the next fire times for a given cron expression.

  **Query Parameters:**

  - `cron`: The cron expression to preview.
  - `count` (optional): The number of fire times to return.

## Site Probing

The Site Probing API allows you to check the availability of a site.

### Endpoints

#### Probe a site

- **`POST /v1/jobs/probe`**

  Probes a site to check its availability and get information about it.

  **Request Body:**

  ```json
  {
    "url": "https://example.com"
  }
  ```

## Webhooks

The Webhooks API allows you to trigger jobs using webhooks.

### Endpoints

#### Trigger a job using a webhook

- **`POST /v1/webhooks/jobs/{token}`**

  Triggers a job using a webhook. The `token` is a unique identifier for the job.

