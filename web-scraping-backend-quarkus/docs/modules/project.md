## Project Module

### Purpose

The Project module groups jobs into projects, and manages who has access to a project through membership and invitations. A project is the unit of collaboration between several users on a related set of scraping jobs.

### Project Management

1. `POST /v1/projects`. Creates a new project. Example body: `{"name": "My new project", "description": "This is a project for scraping e-commerce sites."}`.
2. `GET /v1/projects`. Retrieves a paginated list of projects. Query parameters: `page`, `size`, `search`, `status` (`ACTIVE` or `ARCHIVED`), `createdFrom` and `createdTo` (ISO 8601), `sortBy` (for example `createdAt` or `name`), `sortDirection` (`asc` or `desc`).
3. `GET /v1/projects/{id}`. Retrieves a single project by id.
4. `PUT /v1/projects/{id}`. Updates a project.
5. `DELETE /v1/projects/{id}`. Deletes a project.
6. `GET /v1/projects/stats`. Retrieves aggregate project statistics.
7. `GET /v1/projects/{id}/jobs`. Retrieves the jobs that belong to a project.
8. `POST /v1/projects/{id}/jobs`. Adds a job to a project.
9. `DELETE /v1/projects/{id}/jobs/{jobId}`. Removes a job from a project.

### Project Membership

1. `GET /v1/projects/{id}/members`. Retrieves the members of a project.
2. `POST /v1/projects/{id}/members`. Adds a member to a project.
3. `PUT /v1/projects/{id}/members/{memberId}`. Updates a member's role in a project.
4. `DELETE /v1/projects/{id}/members/{memberId}`. Removes a member from a project.

### Project Invitations

1. `POST /v1/projects/{id}/invitations`. Invites a user to join a project.
2. `GET /v1/projects/{id}/invitations`. Retrieves the invitations for a project.
3. `DELETE /v1/projects/{id}/invitations/{invitationId}`. Cancels a pending invitation.
4. `GET /v1/invitations/me`. Retrieves the invitations addressed to the currently authenticated user.
5. `POST /v1/invitations/{invitationId}/respond`. Responds to an invitation. The action is either `ACCEPT` or `DECLINE`.

When an invitation is created, the invited user receives a `PROJECT_INVITATION` notification. See the [Notification Module](./notification.md).
