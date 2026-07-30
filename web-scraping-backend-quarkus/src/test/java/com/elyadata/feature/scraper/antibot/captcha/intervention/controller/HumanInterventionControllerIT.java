package com.elyadata.feature.scraper.antibot.captcha.intervention.controller;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.antibot.captcha.intervention.dto.HumanInterventionRequest;
import com.elyadata.feature.scraper.antibot.captcha.intervention.service.HumanInterventionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HumanInterventionControllerIT {

    @InjectMock
    HumanInterventionService service;

    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BASE_URL = "/v1/captcha-interventions";

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @Order(1)
    @DisplayName("GET /v1/captcha-interventions/{jobId} - returns 200 with pending intervention")
    void getPending_existingJob_returns200() {
        HumanInterventionRequest mockRequest = HumanInterventionRequest.builder()
                .jobId(JOB_ID)
                .pageUrl("https://example.com")
                .captchaType(CaptchaType.RECAPTCHA_V2)
                .siteKey("test-site-key")
                .screenshotBase64("")
                .requestedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(service.findPending(JOB_ID)).thenReturn(Optional.of(mockRequest));

        given()
                .when()
                .get(BASE_URL + "/" + JOB_ID)
                .then()
                .statusCode(200)
                .body("data.jobId", equalTo(JOB_ID.toString()))
                .body("data.captchaType", equalTo("RECAPTCHA_V2"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /v1/captcha-interventions/{jobId} - returns 404 when no pending intervention")
    void getPending_noPendingIntervention_returns404() {
        when(service.findPending(JOB_ID)).thenReturn(Optional.empty());

        given()
                .when()
                .get(BASE_URL + "/" + JOB_ID)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(3)
    @DisplayName("POST /v1/captcha-interventions/{jobId}/submit - returns 200 when token accepted")
    void submitToken_valid_returns200() {
        when(service.submitToken(eq(JOB_ID), eq("valid-captcha-token"))).thenReturn(true);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"token": "valid-captcha-token"}
                        """)
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/submit")
                .then()
                .statusCode(200)
                .body("data", containsString("accepted"));

        verify(service).submitToken(JOB_ID, "valid-captcha-token");
    }

    @Test
    @Order(4)
    @DisplayName("POST /v1/captcha-interventions/{jobId}/submit - returns 400 when token is blank")
    void submitToken_blankToken_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"token": ""}
                        """)
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/submit")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(5)
    @DisplayName("POST /v1/captcha-interventions/{jobId}/submit - returns 404 when no pending intervention")
    void submitToken_notFound_returns404() {
        when(service.submitToken(eq(JOB_ID), any())).thenReturn(false);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"token": "some-token"}
                        """)
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/submit")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(6)
    @DisplayName("POST /v1/captcha-interventions/{jobId}/cancel - returns 200 when cancelled")
    void cancel_pending_returns200() {
        when(service.cancel(JOB_ID)).thenReturn(true);

        given()
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/cancel")
                .then()
                .statusCode(200);

        verify(service).cancel(JOB_ID);
    }

    @Test
    @Order(7)
    @DisplayName("POST /v1/captcha-interventions/{jobId}/cancel - returns 404 when no pending intervention")
    void cancel_notFound_returns404() {
        when(service.cancel(JOB_ID)).thenReturn(false);

        given()
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/cancel")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(8)
    @DisplayName("POST /v1/captcha-interventions/{jobId}/confirm - returns 200 when confirmed")
    void confirm_pending_returns200() {
        when(service.confirmResolved(JOB_ID)).thenReturn(true);

        given()
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/confirm")
                .then()
                .statusCode(200);

        verify(service).confirmResolved(JOB_ID);
    }

    @Test
    @Order(9)
    @DisplayName("POST /v1/captcha-interventions/{jobId}/confirm - returns 404 when not found")
    void confirm_notFound_returns404() {
        when(service.confirmResolved(JOB_ID)).thenReturn(false);

        given()
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/confirm")
                .then()
                .statusCode(404);
    }
}
