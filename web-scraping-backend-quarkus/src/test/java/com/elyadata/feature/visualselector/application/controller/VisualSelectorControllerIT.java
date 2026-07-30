package com.elyadata.feature.visualselector.application.controller;

import com.elyadata.feature.visualselector.application.dto.SnapshotResponse;
import com.elyadata.feature.visualselector.domain.model.HardenedSelector;
import com.elyadata.feature.visualselector.domain.service.SelectorHardener;
import com.elyadata.feature.visualselector.domain.service.SnapshotRenderService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VisualSelectorControllerIT {

    @InjectMock SnapshotRenderService snapshotRenderService;
    @InjectMock SelectorHardener selectorHardener;

    private static final String BASE = "/v1/visual-selector";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test @Order(1)
    @DisplayName("POST /v1/visual-selector/snapshot - returns 200 with snapshot")
    void snapshot_returns200() {
        SnapshotResponse snapshotResponse = SnapshotResponse.builder()
                .finalUrl("https://example.com")
                .html("<html><body><h1>Test</h1></body></html>")
                .title("Test Page")
                .htmlLength(42)
                .build();

        when(snapshotRenderService.render(any())).thenReturn(snapshotResponse);

        given().spec(spec)
                .body("""
                        {
                          "url": "https://example.com",
                          "stealthMode": false
                        }
                        """)
                .when().post(BASE + "/snapshot")
                .then().statusCode(200)
                .body("data.finalUrl", equalTo("https://example.com"))
                .body("data.title", equalTo("Test Page"))
                .body("data.htmlLength", equalTo(42));
    }

    @Test @Order(2)
    @DisplayName("POST /v1/visual-selector/snapshot - returns 400 when url is blank")
    void snapshot_blankUrl_returns400() {
        given().spec(spec)
                .body("""
                        {
                          "url": "",
                          "stealthMode": false
                        }
                        """)
                .when().post(BASE + "/snapshot")
                .then().statusCode(400);
    }

    @Test @Order(3)
    @DisplayName("POST /v1/visual-selector/snapshot - returns 400 for invalid URL scheme")
    void snapshot_invalidScheme_returns400() {
        given().spec(spec)
                .body("""
                        {
                          "url": "ftp://example.com",
                          "stealthMode": false
                        }
                        """)
                .when().post(BASE + "/snapshot")
                .then().statusCode(400);
    }

    @Test @Order(4)
    @DisplayName("POST /v1/visual-selector/harden - returns 200 with hardened selector")
    void harden_returns200() {
        HardenedSelector hardened = HardenedSelector.builder()
                .cssSelector("h1.product-title")
                .xpath("//h1[@class='product-title']")
                .matchCount(1)
                .unique(true)
                .previewValues(List.of("Awesome Product"))
                .suggestedName("productTitle")
                .build();

        when(selectorHardener.harden(any())).thenReturn(hardened);

        given().spec(spec)
                .body("""
                        {
                          "html": "<html><body><h1 class='product-title'>Awesome Product</h1></body></html>",
                          "candidateSelector": "h1",
                          "expectList": false
                        }
                        """)
                .when().post(BASE + "/harden")
                .then().statusCode(200)
                .body("data.cssSelector", equalTo("h1.product-title"))
                .body("data.unique", equalTo(true))
                .body("data.previewValues", hasSize(1));
    }

    @Test @Order(5)
    @DisplayName("POST /v1/visual-selector/harden - returns 400 when html is blank")
    void harden_blankHtml_returns400() {
        given().spec(spec)
                .body("""
                        {
                          "html": "",
                          "candidateSelector": "h1",
                          "expectList": false
                        }
                        """)
                .when().post(BASE + "/harden")
                .then().statusCode(400);
    }
}
