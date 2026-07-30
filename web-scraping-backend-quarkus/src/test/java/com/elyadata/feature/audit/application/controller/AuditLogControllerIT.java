package com.elyadata.feature.audit.application.controller;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.audit.application.dto.AuditLogResponse;
import com.elyadata.feature.audit.application.dto.AuditLogStatsResponse;
import com.elyadata.feature.audit.application.mapper.AuditLogRestMapper;
import com.elyadata.feature.audit.domain.model.AuditAction;
import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.domain.model.AuditLogStats;
import com.elyadata.feature.audit.domain.model.AuditOutcome;
import com.elyadata.feature.audit.domain.model.AuditResource;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.audit.domain.service.AuditLogService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditLogControllerIT {

    @InjectMock AuditLogService auditLogService;
    @InjectMock AuditLogRestMapper mapper;
    @InjectMock BffTokenService bffToken;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOG_ID   = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String BASE   = "/v1/audit-logs";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        when(bffToken.hasRole("ADMIN")).thenReturn(true);
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private AuditLog anAuditLog() {
        return AuditLog.builder()
                .id(LOG_ID).occurredAt(Instant.now()).userId(USER_ID)
                .username("admin").userRole("ADMIN")
                .action(AuditAction.LOGIN).resourceType(AuditResource.USER)
                .resourceId("res-1").httpMethod("POST").endpoint("/v1/auth/login")
                .httpStatus(200).outcome(AuditOutcome.SUCCESS)
                .build();
    }

    private AuditLogResponse aResponse() {
        return AuditLogResponse.builder()
                .id(LOG_ID).occurredAt(Instant.now()).userId(USER_ID)
                .username("admin").action(AuditAction.LOGIN)
                .resourceType(AuditResource.USER).resourceId("res-1")
                .httpMethod("POST").endpoint("/v1/auth/login")
                .httpStatus(200).outcome(AuditOutcome.SUCCESS)
                .build();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/audit-logs - admin returns 200 with paged list")
    void search_asAdmin_returns200() {
        AuditLog log = anAuditLog();
        AuditLogResponse resp = aResponse();
        Page<AuditLog> page = new Page<>(List.of(log), 1L, 0, 20);

        when(auditLogService.search(any(), anyInt(), anyInt())).thenReturn(page);
        when(mapper.toRest(List.of(log))).thenReturn(List.of(resp));

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.totalElements", equalTo(1));
    }

    @Test @Order(2)
    @DisplayName("GET /v1/audit-logs - non-admin returns 403")
    void search_nonAdmin_returns403() {
        when(bffToken.hasRole("ADMIN")).thenReturn(false);

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(403);
    }

    @Test @Order(3)
    @DisplayName("GET /v1/audit-logs/stats - admin returns 200")
    void getStats_asAdmin_returns200() {
        AuditLogStats stats = AuditLogStats.builder()
                .total(100L).today(10L).failuresLast24h(2L).uniqueUsersLast24h(5L).build();
        AuditLogStatsResponse statsResp = AuditLogStatsResponse.builder()
                .total(100L).today(10L).failuresLast24h(2L).uniqueUsersLast24h(5L).build();

        when(auditLogService.getStats()).thenReturn(stats);
        when(mapper.toRest(stats)).thenReturn(statsResp);

        given().spec(spec)
                .when().get(BASE + "/stats")
                .then().statusCode(200)
                .body("data", notNullValue());
    }

    @Test @Order(4)
    @DisplayName("GET /v1/audit-logs/{id} - returns 200 with audit log detail")
    void getById_found_returns200() {
        AuditLog log = anAuditLog();
        AuditLogResponse resp = aResponse();

        when(auditLogService.getById(LOG_ID)).thenReturn(log);
        when(mapper.toRest(log)).thenReturn(resp);

        given().spec(spec)
                .when().get(BASE + "/" + LOG_ID)
                .then().statusCode(200)
                .body("data.id", equalTo(LOG_ID.toString()));
    }

    @Test @Order(5)
    @DisplayName("GET /v1/audit-logs/{id} - returns 404 when not found")
    void getById_notFound_returns404() {
        when(auditLogService.getById(LOG_ID))
                .thenThrow(DomainException.notFound("AuditLog not found: " + LOG_ID));

        given().spec(spec)
                .when().get(BASE + "/" + LOG_ID)
                .then().statusCode(404);
    }

    @Test @Order(6)
    @DisplayName("DELETE /v1/audit-logs/{id} - admin returns 204")
    void delete_asAdmin_returns204() {
        doNothing().when(auditLogService).delete(LOG_ID);

        given().spec(spec)
                .when().delete(BASE + "/" + LOG_ID)
                .then().statusCode(204);

        verify(auditLogService).delete(LOG_ID);
    }

    @Test @Order(7)
    @DisplayName("GET /v1/audit-logs with date filter - returns 200")
    void search_withDateFilter_returns200() {
        Page<AuditLog> page = new Page<>(List.of(), 0L, 0, 20);
        when(auditLogService.search(any(), anyInt(), anyInt())).thenReturn(page);
        when(mapper.toRest(List.of())).thenReturn(List.of());

        given().spec(spec)
                .queryParam("occurredFrom", "2024-01-01T00:00:00Z")
                .queryParam("occurredTo", "2024-12-31T23:59:59Z")
                .when().get(BASE)
                .then().statusCode(200);
    }
}
