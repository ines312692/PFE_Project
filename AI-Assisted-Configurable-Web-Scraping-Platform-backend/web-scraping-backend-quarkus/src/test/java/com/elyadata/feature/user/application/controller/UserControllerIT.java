package com.elyadata.feature.user.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.user.application.dto.UserResponse;
import com.elyadata.feature.user.application.mapper.UserRestMapper;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.model.UserRole;
import com.elyadata.feature.user.domain.service.UserService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerIT {

    @InjectMock UserService     userService;
    @InjectMock UserRestMapper  userRestMapper;
    @InjectMock BffTokenService bffToken;

    private static final UUID USER_ID  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String BASE_URL = "/v1/users";

    private User aUser() {
        return User.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .role(UserRole.OPERATOR)
                .enabled(true)
                .createdBy(ADMIN_ID)
                .createdAt(Instant.now())
                .build();
    }

    private UserResponse aUserResponse() {
        return UserResponse.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .role(UserRole.OPERATOR)
                .enabled(true)
                .createdBy(ADMIN_ID)
                .build();
    }

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(ADMIN_ID);
        when(bffToken.hasRole("ADMIN")).thenReturn(true);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/users - admin should return 200 with list")
    void getAllUsers_adminRole_returns200() {
        when(userService.getAllUsers()).thenReturn(List.of(aUser()));
        when(userRestMapper.toRest(List.of(aUser()))).thenReturn(List.of(aUserResponse()));

        given().contentType(ContentType.JSON)
            .when().get(BASE_URL)
            .then().statusCode(200)
                   .body("data", hasSize(1))
                   .body("data[0].username", equalTo("testuser"));
    }

    @Test @Order(2)
    @DisplayName("GET /v1/users - non-admin should return 403")
    void getAllUsers_nonAdminRole_returns403() {
        when(bffToken.hasRole("ADMIN")).thenReturn(false);

        given().contentType(ContentType.JSON)
            .when().get(BASE_URL)
            .then().statusCode(403);

        verify(userService, never()).getAllUsers();
    }

    @Test @Order(3)
    @DisplayName("GET /v1/users/{id} - should return 200 with user")
    void getUserById_existingId_returns200() {
        when(userService.getUserById(USER_ID)).thenReturn(aUser());
        when(userRestMapper.toRest(aUser())).thenReturn(aUserResponse());

        given().contentType(ContentType.JSON)
            .when().get(BASE_URL + "/" + USER_ID)
            .then().statusCode(200)
                   .body("data.id", equalTo(USER_ID.toString()))
                   .body("data.username", equalTo("testuser"));
    }

    @Test @Order(4)
    @DisplayName("GET /v1/users/{id} - should return 404 when user not found")
    void getUserById_unknownId_returns404() {
        when(userService.getUserById(USER_ID))
                .thenThrow(com.elyadata.common.exceptions.domain.DomainException
                        .notFound("User not found: " + USER_ID));

        given().contentType(ContentType.JSON)
            .when().get(BASE_URL + "/" + USER_ID)
            .then().statusCode(404);
    }

    @Test @Order(5)
    @DisplayName("POST /v1/users - should return 201 with created user")
    void createUser_validRequest_returns201() {
        String body = """
            {
              "username": "newuser",
              "email": "new@example.com",
              "role": "OPERATOR"
            }
            """;

        User created = aUser();
        UserResponse response = aUserResponse();

        when(userRestMapper.toDomain(any(com.elyadata.feature.user.application.dto.CreateUserRequest.class)))
                .thenReturn(created);
        when(userService.createUser(any(), eq(ADMIN_ID))).thenReturn(created);
        when(userRestMapper.toRest(created)).thenReturn(response);

        given().contentType(ContentType.JSON).body(body)
            .when().post(BASE_URL)
            .then().statusCode(201)
                   .body("data.username", equalTo("testuser"));
    }

    @Test @Order(6)
    @DisplayName("POST /v1/users - should return 400 when body invalid")
    void createUser_invalidBody_returns400() {
        String invalidBody = """
            {
              "username": ""
            }
            """;

        given().contentType(ContentType.JSON).body(invalidBody)
            .when().post(BASE_URL)
            .then().statusCode(400);

        verify(userService, never()).createUser(any(), any());
    }

    @Test @Order(7)
    @DisplayName("POST /v1/users - non-admin should return 403")
    void createUser_nonAdmin_returns403() {
        when(bffToken.hasRole("ADMIN")).thenReturn(false);

        given().contentType(ContentType.JSON)
               .body("{\"username\":\"u\",\"email\":\"e@e.com\",\"role\":\"READER\"}")
            .when().post(BASE_URL)
            .then().statusCode(403);

        verify(userService, never()).createUser(any(), any());
    }

    @Test @Order(8)
    @DisplayName("PATCH /v1/users/{id} - should return 200 with updated user")
    void updateUser_validRequest_returns200() {
        String body = """
            {
              "username": "updated"
            }
            """;

        User updated = aUser().toBuilder().username("updated").build();
        UserResponse response = aUserResponse();

        when(userRestMapper.toDomain(any(com.elyadata.feature.user.application.dto.UpdateUserRequest.class)))
                .thenReturn(updated);
        when(userService.updateUser(eq(USER_ID), any(), eq(ADMIN_ID))).thenReturn(updated);
        when(userRestMapper.toRest(updated)).thenReturn(response);

        given().contentType(ContentType.JSON).body(body)
            .when().patch(BASE_URL + "/" + USER_ID)
            .then().statusCode(200);
    }
    @Test @Order(9)
    @DisplayName("DELETE /v1/users/{id} - should return 204")
    void deleteUser_existingId_returns204() {
        doNothing().when(userService).deleteUser(USER_ID);

        given().contentType(ContentType.JSON)
            .when().delete(BASE_URL + "/" + USER_ID)
            .then().statusCode(204);

        verify(userService).deleteUser(USER_ID);
    }

    @Test @Order(10)
    @DisplayName("DELETE /v1/users/{id} - non-admin should return 403")
    void deleteUser_nonAdmin_returns403() {
        when(bffToken.hasRole("ADMIN")).thenReturn(false);

        given().contentType(ContentType.JSON)
            .when().delete(BASE_URL + "/" + USER_ID)
            .then().statusCode(403);

        verify(userService, never()).deleteUser(any());
    }
    @Test @Order(11)
    @DisplayName("GET /v1/account - should return current user profile")
    void getMyProfile_authenticatedUser_returns200() {
        when(userService.getUserById(ADMIN_ID)).thenReturn(aUser());
        when(userRestMapper.toRest(aUser())).thenReturn(aUserResponse());

        given().contentType(ContentType.JSON)
            .when().get("/v1/account")
            .then().statusCode(200)
                   .body("data.username", equalTo("testuser"));
    }
}
