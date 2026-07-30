package com.elyadata.feature.etl.application.controller;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExportDownloadControllerIT {

    @InjectMock ExportStorageService storageService;

    private static final String BASE      = "/v1/exports";
    private static final String EXPORT_ID = "export-abc-123";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/exports/{id}/download - returns file content when export exists")
    void download_found_returnsFileContent() {
        byte[] content = "id,name\n1,Test".getBytes();
        ExportStorageService.ExportEntry entry = new ExportStorageService.ExportEntry(
                content, "export.csv", "text/csv", Instant.now());

        when(storageService.get(EXPORT_ID)).thenReturn(Optional.of(entry));

        given().spec(spec)
                .when().get(BASE + "/" + EXPORT_ID + "/download")
                .then().statusCode(200)
                .header("Content-Disposition", containsString("export.csv"))
                .header("Content-Length", equalTo(String.valueOf(content.length)));
    }

    @Test @Order(2)
    @DisplayName("GET /v1/exports/{id}/download - returns 404 when export not found")
    void download_notFound_returns404() {
        when(storageService.get(EXPORT_ID)).thenReturn(Optional.empty());

        given().spec(spec)
                .when().get(BASE + "/" + EXPORT_ID + "/download")
                .then().statusCode(404);
    }

    @Test @Order(3)
    @DisplayName("GET /v1/exports/{id}/download - expired export returns 404")
    void download_expired_returns404() {
        when(storageService.get("expired-id")).thenReturn(Optional.empty());

        given().spec(spec)
                .when().get(BASE + "/expired-id/download")
                .then().statusCode(404);
    }
}
