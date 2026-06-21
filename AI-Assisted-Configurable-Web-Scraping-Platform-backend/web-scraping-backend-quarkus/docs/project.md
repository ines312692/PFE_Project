# Project Module

The Project module is responsible for managing projects, which are collections of web scraping jobs. It also handles project membership and invitations.

## Project Management

The Project Management API allows you to create, retrieve, update, and delete projects.

### Endpoints

#### Create a new project

- **`POST /v1/projects`**

  Creates a new project.

  **Request Body:**

  ```json
  {
    "name": "My new project",
    "description": "This is a project for scraping e-commerce sites."
  }
  ```

#### Search for projects

- **`GET /v1/projects`**

  Retrieves a paginated list of projects.

  **Query Parameters:**

  - `page` (optional): The page number to retrieve.
  - `size` (optional): The number of projects per page.
  - `search` (optional): A search term to filter projects by name.
  - `status` (optional): The status of the projects to retrieve (e.g., `ACTIVE`, `ARCHIVED`).
  - `createdFrom` (optional): The start of the creation date range in ISO 8601 format.
  - `createdTo` (optional): The end of the creation date range in ISO 8601 format.
  - `sortBy` (optional): The field to sort by (e.g., `createdAt`, `name`).
  - `sortDirection` (optional): The sort direction (`asc` or `desc`).

#### Retrieve a project by its ID

- **`GET /v1/projects/{id}`**

  Retrieves a single project by its unique identifier.

#### Update a project

- **`PUT /v1/projects/{id}`**

  Updates an existing project.

  **Request Body:**

  ```json
  {
    "name": "My updated project",
    "description": "This is an updated description."
  }
  ```

#### Delete a project

- **`DELETE /v1/projects/{id}`**

  Deletes a project.

#### Retrieve project statistics

- **`GET /v1/projects/stats`**

  Retrieves statistics about the projects.

#### Retrieve jobs for a project

- **`GET /v1/projects/{id}/jobs`**

  Retrieves the jobs associated with a specific project.

#### Add a job to a project

- **`POST /v1/projects/{id}/jobs`**

  Adds a job to a project.

#### Remove a job from a project

- **`DELETE /v1/projects/{id}/jobs/{jobId}`**

  Removes a job from a project.

## Project Membership

The Project Membership API allows you to manage the members of a project.

### Endpoints

#### Retrieve project members

- **`GET /v1/projects/{id}/members`**

  Retrieves the members of a project.

#### Add a member to a project

- **`POST /v1/projects/{id}/members`**

  Adds a member to a project.

#### Update a member's role in a project

- **`PUT /v1/projects/{id}/members/{memberId}`**

  Updates a member's role in a project.

#### Remove a member from a project

- **`DELETE /v1/projects/{id}/members/{memberId}`**

  Removes a member from a project.

## Project Invitations

The Project Invitations API allows you to manage invitations to join a project.

### Endpoints

#### Invite a user to a project

- **`POST /v1/projects/{id}/invitations`**

  Invites a user to join a project.

#### Retrieve project invitations

- **`GET /v1/projects/{id}/invitations`**

  Retrieves the invitations for a project.

#### Cancel a project invitation

- **`DELETE /v1/projects/{id}/invitations/{invitationId}`**

  Cancels a project invitation.

#### Get my invitations

- **`GET /v1/invitations/me`**

  Retrieves the invitations for the currently authenticated user.

#### Respond to an invitation

- **`POST /v1/invitations/{invitationId}/respond`**

  Responds to an invitation to join a project. The action can be `ACCEPT` or `DECLINE`.

