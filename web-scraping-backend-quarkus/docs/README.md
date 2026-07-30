## Documentation

This directory contains the documentation for the AI Assisted Configurable Web Scraping Platform backend.

### Architecture

1. [Application Overview](./architecture/overview.md). Purpose, technology stack, module map, and the overall request flow.
2. [Distributed Job Processing](./architecture/distributed_jobs.md). The full lifecycle of a job on top of Redis Streams, including the distributed crawl coordination, worker leases, and failure recovery.

### Modules

1. [Auth Module](./modules/auth.md)
2. [User and Account Module](./modules/user.md)
3. [Project Module](./modules/project.md)
4. [Job Module](./modules/job.md)
5. [Scraper Module](./modules/scraper.md)
6. [Proxy Module](./modules/proxy.md)
7. [Schema Module](./modules/schema.md)
8. [Scrape Result Module](./modules/scraperesult.md)
9. [ETL Module](./modules/etl.md)
10. [Data Version Module](./modules/dataversion.md)
11. [Template Module](./modules/template.md)
12. [Visual Selector Module](./modules/visualselector.md)
13. [Audit Module](./modules/audit.md)
14. [Notification Module](./modules/notification.md)
15. [Monitoring Module](./modules/monitoring.md)

### Operations

1. [Configuration](./operations/configuration.md). Profiles and the environment variables that matter for running the application.
2. [Deployment](./operations/deployment.md). Local development, the production container, and everyday build commands.
3. [Testing](./operations/testing.md). Unit tests, integration tests, and the real end to end tests of the distributed pipeline.

### Diagrams

The [diagrams](./diagrams) folder contains PlantUML source files: a C4 style context, container, component and deployment set, a sequence and component set specific to the distributed crawl described in [Distributed Job Processing](./architecture/distributed_jobs.md), and a set of earlier class and flow diagrams kept for historical reference under `diagrams/legacy`.
