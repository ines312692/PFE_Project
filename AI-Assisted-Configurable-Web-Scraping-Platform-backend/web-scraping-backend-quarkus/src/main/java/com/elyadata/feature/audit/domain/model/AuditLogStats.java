package com.elyadata.feature.audit.domain.model;

import lombok.Builder;

@Builder
public record AuditLogStats(
        long total,
        long today,
        long failuresLast24h,
        long uniqueUsersLast24h
) {
}