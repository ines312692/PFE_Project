## Audit Module

### Purpose

The Audit module provides a compliance and security trail of write operations and login events performed through the API, recording who did what, to which resource, when, from where, and with what outcome. Entries are created automatically by a request filter rather than through manual calls scattered across other modules.

### Endpoints

All of the following are restricted to administrators.

1. `GET /v1/audit-logs`. Paginated, filterable search over audit entries. Filters include free text, user id, action, resource type, resource id, outcome, and a date range.
2. `GET /v1/audit-logs/stats`. Aggregate statistics, total entries, entries today, failures in the last twenty four hours, and unique users in the last twenty four hours.
3. `GET /v1/audit-logs/{id}`. Retrieves a single entry by id.
4. `DELETE /v1/audit-logs/{id}`. Deletes an entry.

Access to the audit log endpoints is itself excluded from auditing, to avoid recursive noise.

### How Entries Are Created

A global request filter inspects every API call. Read only requests, and requests under the audit log path itself, are skipped. For everything else, the filter determines the action, for example create, update, delete, launch, pause, duplicate, a role change, a secret rotation, or an export, either from an explicit annotation on the endpoint or by inferring it from the path and HTTP method. It resolves the resource type similarly, for example job, user, project, proxy, schema, template, webhook, ETL, export, or auth. The outcome is derived from the HTTP status code. User identity for the entry comes from the authenticated session.

### Sensitive Data Masking

Before a request body is attached to an audit entry, field values for keys such as password, secret, client secret, webhook secret, API key, and any kind of token or authorization header are replaced with a masked placeholder, while the field name itself is kept for context.

### Entry Fields

An audit entry records when it occurred, the acting user's id, username and role, the action, the resource type and id, the HTTP method, endpoint and status, the outcome, the source IP address, the user agent, a correlation id for tracing, a masked summary of the request body, and an error message when applicable.

### Related Modules

Every module that exposes write endpoints is a source of audit entries, since auditing is applied globally rather than per module.
