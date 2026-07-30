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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@QuarkusTest
@TestProfile(DistributedStreamTestProfile.class)
class DistributedCrawlJobE2EIT {

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
        fixture = LocalFixtureServer.startCrawlFixture();
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
    @DisplayName("A CRAWLER job dispatched through the real Redis stream distributes work over the shared frontier and persists once")
    void crawlJob_realDistributedDispatch_crawlsAllPagesAndPersistsOnce() {
        String createBody = """
                {
                  "name": "Distributed Crawl E2E",
                  "targetUrl": "%s/",
                  "jobType": "CRAWLER",
                  "priority": "NORMAL",
                  "triggerType": "MANUAL",
                  "extractionMode": "AUTO",
                  "crawlerConfig": { "maxPages": 10, "maxDepth": 3, "followRedirects": true, "respectRobotsTxt": false }
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

        await().atMost(Duration.ofSeconds(150))
                .pollInterval(Duration.ofSeconds(2))
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

        List<String> htmlBlobs = given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + jobId + "/results")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("completed"))
                .body("data.pagesCrawled", greaterThanOrEqualTo(4))
                .body("data.records", hasSize(greaterThanOrEqualTo(4)))
                .extract().jsonPath().getList("data.records.html", String.class);

        for (String expectedHeading : List.of("Root", "Page One", "Page Two", "Page Three")) {
            org.assertj.core.api.Assertions.assertThat(htmlBlobs)
                    .as("crawled pages should include a page with heading '%s'", expectedHeading)
                    .anyMatch(html -> html.contains(expectedHeading));
        }
        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + jobId + "/history")
                .then()
                .statusCode(200)
                .body("data", hasSize(1));
    }
}
