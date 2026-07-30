package com.elyadata.feature.dataversion.application.controller;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.DataVersionService;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataVersionControllerIT {

    @InjectMock DataVersionService dataVersionService;

    private static final UUID SCRAPE_ID  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VERSION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String BASE     = "/v1/scrape-results/" + SCRAPE_ID + "/versions";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private DataVersion aVersion(int num) {
        return DataVersion.builder()
                .id(VERSION_ID).scrapeResultId(SCRAPE_ID)
                .versionNumber(num)
                .records(List.of(Map.of("field", "value")))
                .appliedSteps(List.of())
                .recordCount(1)
                .label("v" + num)
                .createdAt(Instant.now())
                .build();
    }

    @Test @Order(1)
    @DisplayName("GET /versions - returns 200 with list of versions")
    void list_returns200() {
        when(dataVersionService.listVersions(SCRAPE_ID)).thenReturn(List.of(aVersion(1)));

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data", hasSize(1));
    }

    @Test @Order(2)
    @DisplayName("GET /versions - returns 200 with empty list")
    void list_empty_returns200() {
        when(dataVersionService.listVersions(SCRAPE_ID)).thenReturn(List.of());

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data", hasSize(0));
    }

    @Test @Order(3)
    @DisplayName("GET /versions/{versionNumber} - returns 200 with version detail")
    void get_found_returns200() {
        when(dataVersionService.getVersion(SCRAPE_ID, 1)).thenReturn(aVersion(1));

        given().spec(spec)
                .when().get(BASE + "/1")
                .then().statusCode(200)
                .body("data.versionNumber", equalTo(1))
                .body("data.scrapeResultId", equalTo(SCRAPE_ID.toString()));
    }

    @Test @Order(4)
    @DisplayName("GET /versions/{versionNumber} - returns 404 when not found")
    void get_notFound_returns404() {
        when(dataVersionService.getVersion(SCRAPE_ID, 99))
                .thenThrow(DomainException.notFound("Version not found"));

        given().spec(spec)
                .when().get(BASE + "/99")
                .then().statusCode(404);
    }

    @Test @Order(5)
    @DisplayName("POST /versions/{versionNumber}/rollback - returns 200 with new version")
    void rollback_returns200() {
        DataVersion rolled = aVersion(2);
        when(dataVersionService.rollback(eq(SCRAPE_ID), eq(1), any())).thenReturn(rolled);

        given().spec(spec)
                .when().post(BASE + "/1/rollback")
                .then().statusCode(200)
                .body("data.id", equalTo(VERSION_ID.toString()));
    }
}
