package com.elyadata.feature.template.application.controller;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.template.application.dto.TemplateResponse;
import com.elyadata.feature.template.application.mapper.TemplateRestMapper;
import com.elyadata.feature.template.domain.model.Template;
import com.elyadata.feature.template.domain.model.TemplateCategory;
import com.elyadata.feature.template.domain.service.TemplateService;
import com.elyadata.feature.job.domain.model.JobType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateControllerIT {

    @InjectMock TemplateService templateService;
    @InjectMock TemplateRestMapper templateRestMapper;

    private static final UUID TEMPLATE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final String BASE = "/v1/templates";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private Template aTemplate() {
        return Template.builder()
                .id(TEMPLATE_ID).name("E-Commerce Scraper")
                .description("Scrapes product data from e-commerce sites")
                .category(TemplateCategory.E_COMMERCE)
                .jobType(JobType.SCRAPER)
                .tags(List.of("ecommerce", "products"))
                .popularity(100)
                .config(Map.of("maxPages", 10))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private TemplateResponse aTemplateResponse() {
        return TemplateResponse.builder()
                .id(TEMPLATE_ID).name("E-Commerce Scraper")
                .description("Scrapes product data from e-commerce sites")
                .category("E_COMMERCE").jobType("STRUCTURED")
                .tags(List.of("ecommerce", "products")).popularity(100)
                .config(Map.of("maxPages", 10))
                .build();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/templates - returns 200 with all templates")
    void getAll_returns200() {
        Template template = aTemplate();
        TemplateResponse response = aTemplateResponse();

        when(templateService.getAll()).thenReturn(List.of(template));
        when(templateRestMapper.toResponseList(List.of(template))).thenReturn(List.of(response));

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].name", equalTo("E-Commerce Scraper"))
                .body("data[0].category", equalTo("E_COMMERCE"));
    }

    @Test @Order(2)
    @DisplayName("GET /v1/templates - returns empty list when none exist")
    void getAll_empty_returns200() {
        when(templateService.getAll()).thenReturn(List.of());
        when(templateRestMapper.toResponseList(List.of())).thenReturn(List.of());

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data", hasSize(0));
    }

    @Test @Order(3)
    @DisplayName("GET /v1/templates/{id} - returns 200 with template")
    void getById_found_returns200() {
        Template template = aTemplate();
        TemplateResponse response = aTemplateResponse();

        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateRestMapper.toResponse(template)).thenReturn(response);

        given().spec(spec)
                .when().get(BASE + "/" + TEMPLATE_ID)
                .then().statusCode(200)
                .body("data.id", equalTo(TEMPLATE_ID.toString()))
                .body("data.name", equalTo("E-Commerce Scraper"));
    }

    @Test @Order(4)
    @DisplayName("GET /v1/templates/{id} - returns 404 when not found")
    void getById_notFound_returns404() {
        when(templateService.getById(TEMPLATE_ID))
                .thenThrow(DomainException.notFound("Template not found: " + TEMPLATE_ID));

        given().spec(spec)
                .when().get(BASE + "/" + TEMPLATE_ID)
                .then().statusCode(404);
    }
}
