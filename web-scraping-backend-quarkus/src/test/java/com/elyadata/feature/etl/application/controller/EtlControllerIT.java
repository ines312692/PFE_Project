package com.elyadata.feature.etl.application.controller;

import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.DataVersionService;
import com.elyadata.feature.etl.EtlPipelineRunner;
import com.elyadata.feature.etl.EtlPipelineRunner.EtlRunResult;
import com.elyadata.feature.etl.application.dto.ExportFormat;
import com.elyadata.feature.etl.application.dto.ExportResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EtlControllerIT {

    @InjectMock ScrapeResultPort scrapeResultPort;
    @InjectMock EtlPipelineRunner etlRunner;
    @InjectMock ExportService exportService;
    @InjectMock DataVersionService dataVersionService;

    private static final UUID RESULT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VERSION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String BASE = "/v1/scrape-results/" + RESULT_ID;

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
                .id(RESULT_ID).jobId(UUID.randomUUID())
                .status("SUCCESS")
                .records(List.of(Map.of("title", "Item 1"), Map.of("title", "Item 2")))
                .errors(List.of()).logs(List.of())
                .pagesCrawled(1).durationMs(300L)
                .engineUsed("HTTP").scrapedAt(Instant.now())
                .discoveredFiles(List.of())
                .build();
    }

    private EtlRunResult anEtlResult() {
        return new EtlRunResult(
                List.of(Map.of("title", "Item 1"), Map.of("title", "Item 2")),
                List.of(), 2, 2, 0, 50L, null);
    }

    @Test @Order(1)
    @DisplayName("POST /etl - applies ETL steps and returns 200")
    void applyEtl_returns200() {
        when(scrapeResultPort.findById(RESULT_ID)).thenReturn(Optional.of(aScrapeResult()));
        when(etlRunner.run(any(), any())).thenReturn(anEtlResult());

        given().spec(spec)
                .body("""
                        {
                          "steps": [
                            {"type": "TRIM_WHITESPACE", "params": {}}
                          ]
                        }
                        """)
                .when().post(BASE + "/etl")
                .then().statusCode(200)
                .body("data.rawCount", equalTo(2))
                .body("data.cleanCount", equalTo(2));
    }

    @Test @Order(2)
    @DisplayName("POST /etl - returns 404 when scrape result not found")
    void applyEtl_notFound_returns404() {
        when(scrapeResultPort.findById(RESULT_ID)).thenReturn(Optional.empty());

        given().spec(spec)
                .body("""
                        {
                          "steps": [{"type": "TRIM_WHITESPACE", "params": {}}]
                        }
                        """)
                .when().post(BASE + "/etl")
                .then().statusCode(404);
    }

    @Test @Order(3)
    @DisplayName("POST /etl/apply-and-save - applies, saves and returns 201")
    void applyAndSave_returns201() {
        DataVersion saved = DataVersion.builder()
                .id(VERSION_ID).scrapeResultId(RESULT_ID).versionNumber(1)
                .records(List.of(Map.of("title", "Item 1")))
                .appliedSteps(List.of()).recordCount(1)
                .label("cleaned").createdAt(Instant.now()).build();

        when(scrapeResultPort.findById(RESULT_ID)).thenReturn(Optional.of(aScrapeResult()));
        when(etlRunner.run(any(), any())).thenReturn(anEtlResult());
        when(dataVersionService.createVersion(any())).thenReturn(saved);

        given().spec(spec)
                .body("""
                        {
                          "steps": [{"type": "TRIM_WHITESPACE", "params": {}}],
                          "label": "cleaned"
                        }
                        """)
                .when().post(BASE + "/etl/apply-and-save")
                .then().statusCode(201)
                .body("data.savedVersionId", equalTo(VERSION_ID.toString()))
                .body("data.savedVersionNumber", equalTo(1));
    }

    @Test @Order(4)
    @DisplayName("POST /export - exports scrape result and returns 200")
    void export_returns200() {
        ExportResponse exportResp = new ExportResponse("/v1/exports/abc/download", 2, 30L, "Exported");

        when(scrapeResultPort.findById(RESULT_ID)).thenReturn(Optional.of(aScrapeResult()));
        when(exportService.export(any(), any())).thenReturn(exportResp);

        given().spec(spec)
                .body("""
                        {
                          "format": "CSV",
                          "destination": "DOWNLOAD"
                        }
                        """)
                .when().post(BASE + "/export")
                .then().statusCode(200)
                .body("data.recordsExported", equalTo(2));
    }
}
