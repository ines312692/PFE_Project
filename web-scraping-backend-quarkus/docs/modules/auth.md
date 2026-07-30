## Auth Module

### Purpose

The Auth module handles user authentication through a Backend For Frontend (BFF) pattern in front of Keycloak. The backend never exposes access or refresh tokens to the browser directly. Instead it exchanges the Keycloak authorization code for tokens, stores them in encrypted, HTTP only cookies, and exposes a small set of endpoints that the frontend uses to drive the login flow and to read the current authentication state.

### Endpoints

1. `GET /v1/auth/login`. Redirects the user to the Keycloak login page to start the authentication process. Accepts an optional `theme` query parameter, for example `light` or `dark`.
2. `GET /v1/auth/callback`. Handles the redirect back from Keycloak after a successful login. It exchanges the authorization code for an access token and a refresh token, and stores both in secure, HTTP only cookies.
3. `GET /v1/auth/refresh`. Refreshes the access token using the refresh token cookie. The frontend calls this shortly before the access token expires.
4. `GET /v1/auth/status`. Reports whether the current request is authenticated.
5. `GET /v1/auth/user`. Returns the profile of the currently authenticated user.
6. `GET /v1/auth/logout`. Clears the authentication cookies and redirects to the Keycloak logout page.

### Admin User Management

The following endpoints are restricted to users with the `ADMIN` role.

1. `GET /v1/auth/admin/users`. Lists all users.
2. `POST /v1/auth/admin/users`. Creates a new user.
3. `GET /v1/auth/admin/users/{id}`. Retrieves a user by id.
4. `PUT /v1/auth/admin/users/{id}`. Updates a user.
5. `DELETE /v1/auth/admin/users/{id}`. Deletes a user.
6. `POST /v1/auth/admin/users/{id}/reset-password`. Resets a user's password.
7. `POST /v1/auth/admin/users/{id}/send-verify-email`. Sends a verification email to a user.

### How Authentication Is Checked

A JAX-RS security filter checks incoming requests for the presence of a valid session. `BffTokenService` resolves the current user id and roles from the encrypted cookie, and is the component every other module calls to find out who is making a request and whether that user is an administrator.
