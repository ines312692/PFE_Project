package com.elyadata.feature.audit.application.dto;

import com.elyadata.feature.audit.domain.model.AuditAction;
import com.elyadata.feature.audit.domain.model.AuditOutcome;
import com.elyadata.feature.audit.domain.model.AuditResource;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        UUID id,
        Instant occurredAt,
        UUID userId,
        String username,
        String userRole,
        AuditAction action,
        AuditResource resourceType,
        String resourceId,
        String httpMethod,
        String endpoint,
        Integer httpStatus,
        AuditOutcome outcome,
        String sourceIp,
        String userAgent,
        String correlationId,
        String requestSummary,
        String errorMessage
) {
}