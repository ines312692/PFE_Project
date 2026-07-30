## User and Account Module

### Purpose

This module covers two related concerns: administration of user accounts by an administrator, and self service account management for the currently authenticated user.

### User Management, Administrator Only

1. `POST /v1/users`. Creates a new user. Example body: `{"username": "newuser", "email": "newuser@example.com", "firstName": "New", "lastName": "User", "enabled": true, "roles": ["OPERATOR"]}`.
2. `GET /v1/users`. Lists all users.
3. `GET /v1/users/{id}`. Retrieves a single user by id.
4. `PATCH /v1/users/{id}`. Updates an existing user, for example `{"firstName": "Updated", "lastName": "User"}`.
5. `DELETE /v1/users/{id}`. Deletes a user.

### Account Management, Current User

1. `GET /v1/account`. Retrieves the profile of the currently authenticated user.

### Notes

User records are backed by Keycloak for identity concerns (credentials, email verification, roles) and by a local `users` table for application specific data. Administrator only endpoints are protected the same way as the admin endpoints in the [Auth Module](./auth.md), through the `ADMIN` role resolved by `BffTokenService`.
