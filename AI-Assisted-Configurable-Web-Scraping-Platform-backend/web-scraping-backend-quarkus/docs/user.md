# User and Account Module

The User and Account module is responsible for managing user information and providing account-related functionalities.

## User Management (Admin)

The User Management API allows administrators to manage users in the system.

### Endpoints

#### Create a new user

- **`POST /v1/users`**

  Creates a new user. This endpoint is restricted to administrators.

  **Request Body:**

  ```json
  {
    "username": "newuser",
    "email": "newuser@example.com",
    "firstName": "New",
    "lastName": "User",
    "enabled": true,
    "roles": ["OPERATOR"]
  }
  ```

#### Get all users

- **`GET /v1/users`**

  Retrieves a list of all users. This endpoint is restricted to administrators.

#### Get a user by ID

- **`GET /v1/users/{id}`**

  Retrieves a single user by their unique identifier. This endpoint is restricted to administrators.

#### Update a user

- **`PATCH /v1/users/{id}`**

  Updates an existing user. This endpoint is restricted to administrators.

  **Request Body:**

  ```json
  {
    "firstName": "Updated",
    "lastName": "User"
  }
  ```

#### Delete a user

- **`DELETE /v1/users/{id}`**

  Deletes a user. This endpoint is restricted to administrators.

## Account Management

The Account Management API allows users to manage their own account information.

### Endpoints

#### Get my profile

- **`GET /v1/account`**

  Retrieves the profile of the currently authenticated user.

