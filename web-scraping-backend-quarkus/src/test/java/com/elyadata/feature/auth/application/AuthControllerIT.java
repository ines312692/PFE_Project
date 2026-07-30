package com.elyadata.feature.auth.application;

import com.elyadata.feature.user.domain.service.port.UserPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIT {

    @InjectMock
    UserPort userPort;

    private static final String BASE_URL = "/v1/auth";

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @Order(1)
    @DisplayName("GET /v1/auth/login - should redirect 302 to Keycloak auth")
    void login_returns302Redirect() {
        given()
                .redirects().follow(false)
                .when()
                .get(BASE_URL + "/login")
                .then()
                .statusCode(302)
                .header("Location", notNullValue());
    }

    @Test
    @Order(2)
    @DisplayName("GET /v1/auth/login - with theme param should include theme in redirect URL")
    void login_withTheme_includesThemeInLocation() {
        given()
                .redirects().follow(false)
                .queryParam("theme", "dark")
                .when()
                .get(BASE_URL + "/login")
                .then()
                .statusCode(302)
                .header("Location", containsString("dark"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /v1/auth/register - should redirect 302 to Keycloak registrations")
    void register_returns302Redirect() {
        given()
                .redirects().follow(false)
                .when()
                .get(BASE_URL + "/register")
                .then()
                .statusCode(302)
                .header("Location", containsString("registrations"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /v1/auth/forgot-password - should redirect 302 to Keycloak reset credentials page")
    void forgotPassword_returns302Redirect() {
        given()
                .redirects().follow(false)
                .when()
                .get(BASE_URL + "/forgot-password")
                .then()
                .statusCode(302)
                .header("Location", containsString("reset-credentials"));
    }

    @Test
    @Order(5)
    @DisplayName("GET /v1/auth/callback - with error param should redirect to frontend /login?error=...")
    void callback_withError_redirectsToFrontend() {
        given()
                .redirects().follow(false)
                .queryParam("error", "access_denied")
                .queryParam("state", "some-state")
                .when()
                .get(BASE_URL + "/callback")
                .then()
                .statusCode(302)
                .header("Location", containsString("login?error="));
    }

    @Test
    @Order(6)
    @DisplayName("GET /v1/auth/callback - without matching state cookie should return 400")
    void callback_stateMismatch_returns400() {
        given()
                .queryParam("code", "some-code")
                .queryParam("state", "expected-state")
                .when()
                .get(BASE_URL + "/callback")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(7)
    @DisplayName("GET /v1/auth/me - without access_token cookie should return 401 unauthenticated")
    void me_noAccessToken_returns401Unauthenticated() {
        given()
                .when()
                .get(BASE_URL + "/me")
                .then()
                .statusCode(401)
                .body("authenticated", equalTo(false));
    }

    @Test
    @Order(8)
    @DisplayName("POST /v1/auth/refresh - without refresh_token cookie should return 401")
    void refresh_noRefreshToken_returns401() {
        given()
                .when()
                .post(BASE_URL + "/refresh")
                .then()
                .statusCode(401)
                .body("error", notNullValue());
    }

    @Test
    @Order(9)
    @DisplayName("POST /v1/auth/logout - without refresh_token should still return 200 with loggedOut=true")
    void logout_noRefreshToken_returns200() {
        given()
                .when()
                .post(BASE_URL + "/logout")
                .then()
                .statusCode(200)
                .body("loggedOut", equalTo(true));
    }

    @Test
    @Order(10)
    @DisplayName("POST /v1/auth/logout - should clear access_token and refresh_token cookies")
    void logout_clearsCookies() {
        given()
                .when()
                .post(BASE_URL + "/logout")
                .then()
                .statusCode(200)
                .cookie("access_token", equalTo(""))
                .cookie("refresh_token", equalTo(""));
    }
}