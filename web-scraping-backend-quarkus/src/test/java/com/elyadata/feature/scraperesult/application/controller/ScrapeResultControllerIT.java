package com.elyadata.feature.scraperesult.application.controller;

import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScrapeResultControllerIT {

    @InjectMock ScrapeResultPort scrapeResultPort;

    private static final UUID JOB_ID    = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESULT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String BASE    = "/v1/scrape-results";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private ScrapeResult aScrapeResult() {
        return ScrapeResult.builder()
                .id(RESULT_ID).jobId(JOB_ID)
                .status("SUCCESS")
                .records(List.of(Map.of("title", "Test Item")))
                .errors(List.of()).logs(List.of())
                .pagesCrawled(1).durationMs(500L)
                .engineUsed("PLAYWRIGHT")
                .scrapedAt(Instant.now())
                .discoveredFiles(List.of())
                .build();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/scrape-results/job/{jobId}/latest - returns 200 with latest result")
    void getLatestByJobId_found_returns200() {
        when(scrapeResultPort.findByJobId(JOB_ID)).thenReturn(Optional.of(aScrapeResult()));

        given().spec(spec)
                .when().get(BASE + "/job/" + JOB_ID + "/latest")
                .then().statusCode(200)
                .body("data.id", equalTo(RESULT_ID.toString()))
                .body("data.status", equalTo("SUCCESS"));
    }

    @Test @Order(2)
    @DisplayName("GET /v1/scrape-results/job/{jobId}/latest - returns 404 when not found")
    void getLatestByJobId_notFound_returns404() {
        when(scrapeResultPort.findByJobId(JOB_ID)).thenReturn(Optional.empty());

        given().spec(spec)
                .when().get(BASE + "/job/" + JOB_ID + "/latest")
                .then().statusCode(404);
    }

    @Test @Order(3)
    @DisplayName("GET /v1/scrape-results/job/{jobId} - returns 200 with all results")
    void getAllByJobId_returns200() {
        when(scrapeResultPort.findAllByJobId(JOB_ID)).thenReturn(List.of(aScrapeResult()));

        given().spec(spec)
                .when().get(BASE + "/job/" + JOB_ID)
                .then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].jobId", equalTo(JOB_ID.toString()));
    }

    @Test @Order(4)
    @DisplayName("GET /v1/scrape-results/job/{jobId} - returns empty list")
    void getAllByJobId_empty_returns200() {
        when(scrapeResultPort.findAllByJobId(JOB_ID)).thenReturn(List.of());

        given().spec(spec)
                .when().get(BASE + "/job/" + JOB_ID)
                .then().statusCode(200)
                .body("data", hasSize(0));
    }

    @Test @Order(5)
    @DisplayName("GET /v1/scrape-results/{id} - returns 200 with result by ID")
    void getById_found_returns200() {
        when(scrapeResultPort.findById(RESULT_ID)).thenReturn(Optional.of(aScrapeResult()));

        given().spec(spec)
                .when().get(BASE + "/" + RESULT_ID)
                .then().statusCode(200)
                .body("data.id", equalTo(RESULT_ID.toString()));
    }

    @Test @Order(6)
    @DisplayName("GET /v1/scrape-results/{id} - returns 404 when not found")
    void getById_notFound_returns404() {
        when(scrapeResultPort.findById(RESULT_ID)).thenReturn(Optional.empty());

        given().spec(spec)
                .when().get(BASE + "/" + RESULT_ID)
                .then().statusCode(404);
    }
}
