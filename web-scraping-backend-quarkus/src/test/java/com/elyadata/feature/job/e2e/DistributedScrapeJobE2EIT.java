package com.elyadata.feature.job.e2e;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(DistributedStreamTestProfile.class)
class DistributedScrapeJobE2EIT {

    private static final String BASE_URL = "/v1/jobs";
    private static final UUID USER_ID = UUID.randomUUID();

    private static LocalFixtureServer fixture;

    @InjectMock
    BffTokenService bffToken;
    @InjectMock
    UserPort userPort;

    private RequestSpecification authSpec;

    @BeforeAll
    static void startFixture() {
        fixture = LocalFixtureServer.startScrapeFixture();
    }

    @AfterAll
    static void stopFixture() {
        fixture.close();
    }

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        when(bffToken.hasRole("ADMIN")).thenReturn(false);
        when(userPort.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().id(USER_ID).username("e2e-user").build()));

        authSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @DisplayName("A SCRAPER job dispatched through the real Redis stream completes and persists extracted data")
    void scrapeJob_realDistributedDispatch_completesAndExtractsData() {
        String createBody = """
                {
                  "name": "Distributed Scrape E2E",
                  "targetUrl": "%s/",
                  "jobType": "SCRAPER",
                  "priority": "NORMAL",
                  "triggerType": "MANUAL",
                  "extractionMode": "CSS_SELECTOR",
                  "scraperConfig": { "singlePageMode": true, "scrollToBottom": false },
                  "extractionSchema": {
                    "fields": [
                      { "name": "title", "selector": "#title", "type": "TEXT", "required": false },
                      { "name": "price", "selector": ".price", "type": "TEXT", "required": false }
                    ]
                  }
                }
                """.formatted(fixture.baseUrl());

        String jobId = given()
                .spec(authSpec)
                .body(createBody)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("data.status", equalTo("CREATED"))
                .extract().path("data.id");

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + jobId + "/launch")
                .then()
                .statusCode(200);

        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> given()
                        .spec(authSpec)
                        .when()
                        .get(BASE_URL + "/" + jobId)
                        .then()
                        .statusCode(200)
                        .body("data.status", oneOf("COMPLETED", "FAILED")));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + jobId)
                .then()
                .statusCode(200)
                .body("data.status", equalTo("COMPLETED"));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + jobId + "/results")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("completed"))
                .body("data.records", org.hamcrest.Matchers.hasSize(1))
                .body("data.records[0].title", equalTo("Distributed Scrape Fixture"))
                .body("data.records[0].price", equalTo("42.00"));
    }
}
