## Notification Module

### Purpose

The Notification module delivers both persisted and real time notifications to users about job outcomes, project invitations, and CAPTCHAs that need manual attention.

### Endpoints

1. `GET /v1/notifications`. Lists the current user's notifications.
2. `GET /v1/notifications/unread-count`. Returns the unread count for the current user.
3. `PATCH /v1/notifications/{id}/read`. Marks a single notification as read.
4. `PATCH /v1/notifications/read-all`. Marks all of the current user's notifications as read.
5. `DELETE /v1/notifications/{id}`. Deletes a single notification.
6. `DELETE /v1/notifications`. Deletes all of the current user's notifications.
7. `GET /v1/notifications/stream`. A server sent events stream that pushes new notifications to a connected client in real time.

### Notification Types

1. `JOB_COMPLETED`. Fired when a scrape or crawl job finishes successfully.
2. `JOB_FAILED`. Fired when a scrape or crawl job finishes with a failure.
3. `PROJECT_INVITATION`. Fired when a user is invited to join a project.
4. `CAPTCHA_INTERVENTION_REQUIRED`. Fired when a job hits a CAPTCHA that needs a human to solve it before the job can continue.

### Delivery

A notification is first persisted, then immediately pushed over the server sent events stream to any live connection for that user. A client that is not connected simply reads the notification later through the list endpoint.

### Related Modules

1. [Job Module](./job.md) is the main source of job completion and failure notifications.
2. [Project Module](./project.md) is the source of invitation notifications.
3. [Scraper Module](./scraper.md), specifically the human intervention flow, is the source of CAPTCHA intervention notifications.
