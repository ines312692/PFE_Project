package com.elyadata.feature.schema.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.schema.application.dto.PromptAssistResponse;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse;
import com.elyadata.feature.schema.domain.service.PromptAssistantService;
import com.elyadata.feature.schema.domain.service.SchemaValidatorService;
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
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchemaAssistControllerIT {

    @InjectMock PromptAssistantService promptAssistantService;
    @InjectMock SchemaValidatorService schemaValidatorService;
    @InjectMock BffTokenService bffToken;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String BASE = "/v1/schema/assist";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test @Order(1)
    @DisplayName("POST /v1/schema/assist - GENERATE action returns 200 with suggestion")
    void assist_generate_returns200() {
        PromptAssistResponse response = PromptAssistResponse.ok(
                "Extract products from the page.",
                "{\"name\": \"string\", \"price\": \"number\"}",
                "Generated e-commerce extraction prompt",
                "llama-3.1-8b-instant");

        when(promptAssistantService.assist(any(), eq(USER_ID))).thenReturn(response);

        given().spec(spec)
                .body("""
                        {
                          "action": "GENERATE",
                          "description": "Extract product names and prices from an e-commerce page"
                        }
                        """)
                .when().post(BASE)
                .then().statusCode(200)
                .body("data.success", equalTo(true))
                .body("data.suggestedPrompt", containsString("Extract"));
    }

    @Test @Order(2)
    @DisplayName("POST /v1/schema/assist - returns 200 with error response when LLM fails")
    void assist_llmError_returns200WithError() {
        PromptAssistResponse error = PromptAssistResponse.error("AI assistant is not configured");
        when(promptAssistantService.assist(any(), eq(USER_ID))).thenReturn(error);

        given().spec(spec)
                .body("""
                        {
                          "action": "GENERATE",
                          "description": "Extract something"
                        }
                        """)
                .when().post(BASE)
                .then().statusCode(200)
                .body("data.success", equalTo(false))
                .body("data.errorMessage", notNullValue());
    }

    @Test @Order(3)
    @DisplayName("POST /v1/schema/assist/validate - returns 200 with validation results")
    void validate_returns200() {
        SchemaValidationResponse response = SchemaValidationResponse.builder()
                .success(true).totalFields(2).validFields(2).brokenFields(0)
                .results(List.of())
                .build();

        when(schemaValidatorService.validate(any())).thenReturn(response);

        given().spec(spec)
                .body("""
                        {
                          "targetUrl": "https://example.com/products",
                          "fields": [
                            {"name": "title", "selector": "h1.product-title", "type": "TEXT"},
                            {"name": "price", "selector": ".price", "type": "TEXT"}
                          ]
                        }
                        """)
                .when().post(BASE + "/validate")
                .then().statusCode(200)
                .body("data.success", equalTo(true))
                .body("data.totalFields", equalTo(2))
                .body("data.validFields", equalTo(2));
    }

    @Test @Order(4)
    @DisplayName("POST /v1/schema/assist/validate - returns 400 when fields empty")
    void validate_emptyFields_returns400() {
        given().spec(spec)
                .body("""
                        {
                          "targetUrl": "https://example.com",
                          "fields": []
                        }
                        """)
                .when().post(BASE + "/validate")
                .then().statusCode(400);
    }
}
