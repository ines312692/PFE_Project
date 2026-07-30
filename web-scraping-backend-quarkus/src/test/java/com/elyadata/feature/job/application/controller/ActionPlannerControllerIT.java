package com.elyadata.feature.job.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.dto.ActionPlanResponse;
import com.elyadata.feature.job.domain.service.planner.ActionPlannerService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ActionPlannerControllerIT {

    @InjectMock
    ActionPlannerService actionPlannerService;

    @InjectMock
    BffTokenService bffToken;

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE_URL = "/v1/jobs/action-planner";

    private RequestSpecification authSpec;

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);

        authSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @Order(1)
    @DisplayName("POST /v1/jobs/action-planner - valid request returns 200 with success response")
    void plan_validRequest_returns200() {
        ActionPlanResponse mockResponse = ActionPlanResponse.ok(
                List.of(), "Fill the login form and submit", "llama-3.3-70b-versatile", true);

        when(actionPlannerService.plan(any(), any(UUID.class))).thenReturn(mockResponse);

        given()
                .spec(authSpec)
                .body("""
                        {
                          "targetUrl": "https://example.com/login",
                          "instruction": "Fill the login form with test credentials and click Submit"
                        }
                        """)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(200)
                .body("data.success", equalTo(true))
                .body("data.reasoning", equalTo("Fill the login form and submit"));
    }

    @Test
    @Order(2)
    @DisplayName("POST /v1/jobs/action-planner - blank targetUrl returns 400")
    void plan_blankTargetUrl_returns400() {
        given()
                .spec(authSpec)
                .body("""
                        {
                          "targetUrl": "",
                          "instruction": "click login button"
                        }
                        """)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(actionPlannerService, never()).plan(any(), any());
    }

    @Test
    @Order(3)
    @DisplayName("POST /v1/jobs/action-planner - blank instruction returns 400")
    void plan_blankInstruction_returns400() {
        given()
                .spec(authSpec)
                .body("""
                        {
                          "targetUrl": "https://example.com",
                          "instruction": ""
                        }
                        """)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(actionPlannerService, never()).plan(any(), any());
    }

    @Test
    @Order(4)
    @DisplayName("POST /v1/jobs/action-planner - service returns error response relayed as 200")
    void plan_serviceReturnsError_returns200WithErrorPayload() {
        ActionPlanResponse errorResponse = ActionPlanResponse.error("AI assistant not configured");

        when(actionPlannerService.plan(any(), any(UUID.class))).thenReturn(errorResponse);

        given()
                .spec(authSpec)
                .body("""
                        {
                          "targetUrl": "https://example.com",
                          "instruction": "click the login button"
                        }
                        """)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(200)
                .body("data.success", equalTo(false))
                .body("data.errorMessage", equalTo("AI assistant not configured"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /v1/jobs/action-planner - instruction exceeding 2000 chars returns 400")
    void plan_instructionTooLong_returns400() {
        String longInstruction = "a".repeat(2001);

        given()
                .spec(authSpec)
                .body(String.format("""
                        {
                          "targetUrl": "https://example.com",
                          "instruction": "%s"
                        }
                        """, longInstruction))
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(actionPlannerService, never()).plan(any(), any());
    }
}