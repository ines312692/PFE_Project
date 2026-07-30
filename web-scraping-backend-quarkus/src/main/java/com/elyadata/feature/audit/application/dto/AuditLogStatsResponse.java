package com.elyadata.feature.audit.application.dto;

import lombok.Builder;

@Builder
public record AuditLogStatsResponse(
        long total,
        long today,
        long failuresLast24h,
        long uniqueUsersLast24h
) {
}