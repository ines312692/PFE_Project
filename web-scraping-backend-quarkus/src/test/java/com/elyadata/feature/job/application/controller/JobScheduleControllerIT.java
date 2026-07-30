package com.elyadata.feature.job.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.domain.service.JobScheduler;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobScheduleControllerIT {

    @InjectMock
    JobScheduler scheduler;

    @InjectMock
    BffTokenService bffToken;

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE_URL = "/v1/jobs/schedule";

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
    @DisplayName("GET /v1/jobs/schedule/upcoming - returns 200 with list of upcoming runs")
    void upcoming_returns200WithList() {
        Instant now = Instant.now();
        JobScheduler.UpcomingRun run = new JobScheduler.UpcomingRun(
                UUID.randomUUID(), "My Job", now.plus(1, ChronoUnit.DAYS));

        when(scheduler.upcomingRunsBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(run));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/upcoming")
                .then()
                .statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].jobName", equalTo("My Job"));

        verify(scheduler).upcomingRunsBetween(any(Instant.class), any(Instant.class));
    }

    @Test
    @Order(2)
    @DisplayName("GET /v1/jobs/schedule/upcoming - with explicit from/to range")
    void upcoming_withExplicitRange_passesInstantsToScheduler() {
        when(scheduler.upcomingRunsBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        String from = "2025-01-01T00:00:00Z";
        String to = "2025-01-31T23:59:59Z";

        given()
                .spec(authSpec)
                .queryParam("from", from)
                .queryParam("to", to)
                .when()
                .get(BASE_URL + "/upcoming")
                .then()
                .statusCode(200)
                .body("data", hasSize(0));

        verify(scheduler).upcomingRunsBetween(
                eq(Instant.parse(from)),
                eq(Instant.parse(to))
        );
    }

    @Test
    @Order(3)
    @DisplayName("GET /v1/jobs/schedule/upcoming - empty scheduler returns empty list")
    void upcoming_noJobs_returnsEmptyList() {
        when(scheduler.upcomingRunsBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/upcoming")
                .then()
                .statusCode(200)
                .body("data", hasSize(0));
    }

    @Test
    @Order(4)
    @DisplayName("GET /v1/jobs/schedule/preview - returns 200 with list of next fire times")
    void preview_returns200WithFireTimes() {
        Instant t1 = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant t2 = Instant.now().plus(2, ChronoUnit.DAYS);

        when(scheduler.nextFireTimes(eq("0 12 * * *"), anyInt()))
                .thenReturn(List.of(t1, t2));

        given()
                .spec(authSpec)
                .queryParam("cron", "0 12 * * *")
                .queryParam("count", "2")
                .when()
                .get(BASE_URL + "/preview")
                .then()
                .statusCode(200)
                .body("data.cron", equalTo("0 12 * * *"))
                .body("data.next", hasSize(2));
    }

    @Test
    @Order(5)
    @DisplayName("GET /v1/jobs/schedule/preview - default count of 5 when not specified")
    void preview_defaultCount_usesDefaultOf5() {
        when(scheduler.nextFireTimes(anyString(), anyInt()))
                .thenReturn(List.of());

        given()
                .spec(authSpec)
                .queryParam("cron", "0 0 * * *")
                .when()
                .get(BASE_URL + "/preview")
                .then()
                .statusCode(200);

        verify(scheduler).nextFireTimes(eq("0 0 * * *"), eq(5));
    }

    @Test
    @Order(6)
    @DisplayName("GET /v1/jobs/schedule/preview - count clamped to 50 max")
    void preview_countAboveMax_clampedTo50() {
        when(scheduler.nextFireTimes(anyString(), anyInt()))
                .thenReturn(List.of());

        given()
                .spec(authSpec)
                .queryParam("cron", "0 0 * * *")
                .queryParam("count", "999")
                .when()
                .get(BASE_URL + "/preview")
                .then()
                .statusCode(200);

        verify(scheduler).nextFireTimes(eq("0 0 * * *"), eq(50));
    }
}