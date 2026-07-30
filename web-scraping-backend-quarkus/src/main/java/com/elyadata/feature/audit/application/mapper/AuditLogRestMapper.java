package com.elyadata.feature.audit.application.mapper;

import com.elyadata.feature.audit.application.dto.AuditLogResponse;
import com.elyadata.feature.audit.application.dto.AuditLogStatsResponse;
import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.domain.model.AuditLogStats;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AuditLogRestMapper {

    public AuditLogResponse toRest(AuditLog domain) {
        if (domain == null) return null;
        return AuditLogResponse.builder()
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

    public List<AuditLogResponse> toRest(List<AuditLog> domains) {
        return domains.stream().map(this::toRest).toList();
    }

    public AuditLogStatsResponse toRest(AuditLogStats stats) {
        return AuditLogStatsResponse.builder()
                .total(stats.total())
                .today(stats.today())
                .failuresLast24h(stats.failuresLast24h())
                .uniqueUsersLast24h(stats.uniqueUsersLast24h())
                .build();
    }
}