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
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountControllerIT {

    @InjectMock UserService userService;
    @InjectMock UserRestMapper userRestMapper;
    @InjectMock BffTokenService bffToken;

    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String BASE = "/v1/account";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private User aUser() {
        return User.builder()
                .id(USER_ID).username("testuser").email("test@example.com")
                .role(UserRole.OPERATOR).enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    private UserResponse aUserResponse() {
        return UserResponse.builder()
                .id(USER_ID).username("testuser").email("test@example.com")
                .role(UserRole.OPERATOR).enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/account - returns 200 with current user profile")
    void getMyProfile_returns200() {
        User user = aUser();
        UserResponse response = aUserResponse();

        when(userService.getUserById(USER_ID)).thenReturn(user);
        when(userRestMapper.toRest(user)).thenReturn(response);

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data.id", equalTo(USER_ID.toString()))
                .body("data.username", equalTo("testuser"));
    }

    @Test @Order(2)
    @DisplayName("PATCH /v1/account/avatar - returns 200 with updated user")
    void updateMyAvatar_returns200() {
        User updated = aUser().toBuilder().avatarUrl("https://example.com/avatar.png").build();
        UserResponse response = aUserResponse().toBuilder().avatarUrl("https://example.com/avatar.png").build();

        when(userService.updateAvatar(eq(USER_ID), any())).thenReturn(updated);
        when(userRestMapper.toRest(updated)).thenReturn(response);

        given().spec(spec)
                .body("{\"avatarUrl\": \"https://example.com/avatar.png\"}")
                .when().patch(BASE + "/avatar")
                .then().statusCode(200)
                .body("data.avatarUrl", equalTo("https://example.com/avatar.png"));
    }

    @Test @Order(3)
    @DisplayName("PATCH /v1/account/avatar - returns 400 when avatar URL blank")
    void updateMyAvatar_blankUrl_returns400() {
        given().spec(spec)
                .body("{\"avatarUrl\": \"\"}")
                .when().patch(BASE + "/avatar")
                .then().statusCode(400);
    }
}
