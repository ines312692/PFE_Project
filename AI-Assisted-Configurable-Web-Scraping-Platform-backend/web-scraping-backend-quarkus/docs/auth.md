# Auth Module

The Auth module handles user authentication and authorization. It integrates with Keycloak for identity and access management.

## Endpoints

### Login

- **`GET /v1/auth/login`**

  Redirects the user to the Keycloak login page to initiate the authentication process.

  **Query Parameters:**

  - `theme` (optional): The theme to use for the login page (e.g., `light`, `dark`).

### Logout

- **`GET /v1/auth/logout`**

  Logs the user out by clearing the authentication cookies and redirecting to the Keycloak logout page.

### Authentication Callback

- **`GET /v1/auth/callback`**

  Handles the callback from Keycloak after a successful login. It exchanges the authorization code for an access token and a refresh token, which are then stored in secure, HTTP-only cookies.

### Refresh Token

- **`GET /v1/auth/refresh`**

  Refreshes the access token using the refresh token. This endpoint is typically called by the frontend when the access token is about to expire.

### Authentication Status

- **`GET /v1/auth/status`**

  Checks the user's authentication status. It returns whether the user is authenticated or not.

### User Information

- **`GET /v1/auth/user`**

  Retrieves the profile of the currently authenticated user.

### Admin User Management

The following endpoints are available for users with the `ADMIN` role:

- **`GET /v1/auth/admin/users`**: Retrieves a list of all users.
- **`POST /v1/auth/admin/users`**: Creates a new user.
- **`GET /v1/auth/admin/users/{id}`**: Retrieves a user by their ID.
- **`PUT /v1/auth/admin/users/{id}`**: Updates a user's information.
- **`DELETE /v1/auth/admin/users/{id}`**: Deletes a user.
- **`POST /v1/auth/admin/users/{id}/reset-password`**: Resets a user's password.
- **`POST /v1/auth/admin/users/{id}/send-verify-email`**: Sends a verification email to a user.

