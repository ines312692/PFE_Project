package com.elyadata.feature.audit.infrastructure.mapper;

import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.infrastructure.entity.AuditLogEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuditLogEntityMapper {

    public AuditLogEntity toEntity(AuditLog domain) {
        if (domain == null) return null;
        return AuditLogEntity.builder()
                .id(domain.id())
                .occurredAt(domain.occurredAt())
                .userId(domain.userId())
                .username(domain.username())
                .userRole(domain.userRole())
                .action(domain.action())
                .resourceType(domain.resourceType())
                .resourceId(domain.resourceId())
                .httpMethod(domain.httpMethod())
                .endpoint(domain.endpoint())
                .httpStatus(domain.httpStatus())
                .outcome(domain.outcome())
                .sourceIp(domain.sourceIp())
                .userAgent(domain.userAgent())
                .correlationId(domain.correlationId())
                .requestSummary(domain.requestSummary())
                .errorMessage(domain.errorMessage())
                .build();
    }

    public AuditLog toDomain(AuditLogEntity entity) {
        if (entity == null) return null;
        return AuditLog.builder()
                .id(entity.getId())
                .occurredAt(entity.getOccurredAt())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .userRole(entity.getUserRole())
                .action(entity.getAction())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .httpMethod(entity.getHttpMethod())
                .endpoint(entity.getEndpoint())
                .httpStatus(entity.getHttpStatus())
                .outcome(entity.getOutcome())
                .sourceIp(entity.getSourceIp())
                .userAgent(entity.getUserAgent())
                .correlationId(entity.getCorrelationId())
                .requestSummary(entity.getRequestSummary())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}