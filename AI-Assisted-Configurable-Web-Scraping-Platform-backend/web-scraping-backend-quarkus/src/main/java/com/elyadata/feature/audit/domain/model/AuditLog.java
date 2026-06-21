package com.elyadata.feature.audit.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record AuditLog(
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