package com.elyadata.feature.job.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.dto.SiteProbeResponse;
import com.elyadata.feature.job.domain.service.SiteProbeService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SiteProbeControllerIT {

    @InjectMock
    SiteProbeService siteProbeService;

    @InjectMock
    BffTokenService bffToken;

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE_URL = "/v1/jobs/probe";

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
    @DisplayName("POST /v1/jobs/probe - valid URL returns 200 with probe result")
    void probe_validUrl_returns200() {
        SiteProbeResponse mockResponse = new SiteProbeResponse(
                "https://example.com", List.of(), 200, true
        );

        when(siteProbeService.probe(eq("https://example.com"))).thenReturn(mockResponse);

        given()
                .spec(authSpec)
                .body("""
                        {"url": "https://example.com"}
                        """)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(200)
                .body("data", notNullValue());

        verify(siteProbeService).probe("https://example.com");
    }

    @Test
    @Order(2)
    @DisplayName("POST /v1/jobs/probe - blank URL returns 400 validation error")
    void probe_blankUrl_returns400() {
        given()
                .spec(authSpec)
                .body("""
                        {"url": ""}
                        """)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(siteProbeService, never()).probe(any());
    }

    @Test
    @Order(3)
    @DisplayName("POST /v1/jobs/probe - missing url field returns 400")
    void probe_missingUrl_returns400() {
        given()
                .spec(authSpec)
                .body("{}")
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(siteProbeService, never()).probe(any());
    }
}
