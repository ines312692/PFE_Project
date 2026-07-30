package com.elyadata.feature.audit.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.audit.application.dto.AuditLogResponse;
import com.elyadata.feature.audit.application.dto.AuditLogStatsResponse;
import com.elyadata.feature.audit.application.mapper.AuditLogRestMapper;
import com.elyadata.feature.audit.domain.model.AuditAction;
import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.domain.model.AuditLogSearchCriteria;
import com.elyadata.feature.audit.domain.model.AuditOutcome;
import com.elyadata.feature.audit.domain.model.AuditResource;
import com.elyadata.feature.audit.domain.service.AuditLogService;
import com.elyadata.feature.audit.infrastructure.interceptor.Auditable;
import com.elyadata.feature.auth.application.BffTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Auditable(skip = true)
@ApplicationScoped
@Path("/v1/audit-logs")
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditLogController {

    private static final String AUDIT_PATH = "/v1/audit-logs";

    private final AuditLogService auditLogService;
    private final AuditLogRestMapper mapper;
    private final BffTokenService bffToken;

    @GET
    public Response search(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("search") String search,
            @QueryParam("userId") UUID userId,
            @QueryParam("action") AuditAction action,
            @QueryParam("resourceType") AuditResource resourceType,
            @QueryParam("resourceId") String resourceId,
            @QueryParam("outcome") AuditOutcome outcome,
            @QueryParam("occurredFrom") String occurredFromIso,
            @QueryParam("occurredTo") String occurredToIso,
            @QueryParam("sortBy") @DefaultValue("occurredAt") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        requireAdmin();

        int safeSize = Math.clamp(size, 1, 200);
        int safePage = Math.max(page, 0);

        AuditLogSearchCriteria criteria = AuditLogSearchCriteria.builder()
                .search(search)
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .outcome(outcome)
                .occurredFrom(parseInstant(occurredFromIso))
                .occurredTo(parseInstant(occurredToIso))
                .sortField(parseSortField(sortBy))
                .sortDirection(parseSortDirection(sortDirection))
                .build();

        Page<AuditLog> result = auditLogService.search(criteria, safePage, safeSize);
        List<AuditLogResponse> rows = mapper.toRest(result.content());

        return Response.ok(ApiResponse.paginated(
                rows, result.totalElements(), result.page(), result.size())).build();
    }

    @GET
    @Path("/stats")
    public Response getStats() {
        requireAdmin();
        AuditLogStatsResponse stats = mapper.toRest(auditLogService.getStats());
        return Response.ok(ApiResponse.success(stats)).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        requireAdmin();
        AuditLogResponse response = mapper.toRest(auditLogService.getById(id));
        return Response.ok(ApiResponse.success(response)).build();
    }

    private void requireAdmin() {
        bffToken.resolveCurrentUserId(AUDIT_PATH);
        if (!bffToken.hasRole("ADMIN")) {
            throw DomainException.unauthorized(
                    "Admin role required to access audit logs",
                    AUDIT_PATH);
        }
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO date: " + iso);
        }
    }

    private static AuditLogSearchCriteria.SortField parseSortField(String s) {
        return switch (s == null ? "occurredat" : s.toLowerCase()) {
            case "action" -> AuditLogSearchCriteria.SortField.ACTION;
            case "resourcetype", "resource_type" -> AuditLogSearchCriteria.SortField.RESOURCE_TYPE;
            case "outcome" -> AuditLogSearchCriteria.SortField.OUTCOME;
            default -> AuditLogSearchCriteria.SortField.OCCURRED_AT;
        };
    }

    private static AuditLogSearchCriteria.SortDirection parseSortDirection(String s) {
        return "asc".equalsIgnoreCase(s)
                ? AuditLogSearchCriteria.SortDirection.ASC
                : AuditLogSearchCriteria.SortDirection.DESC;
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        requireAdmin();
        auditLogService.delete(id);
        return Response.noContent().build();
    }
}