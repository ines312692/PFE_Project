package com.elyadata.feature.audit.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AuditLogSearchCriteria(
        String search,
        UUID userId,
        AuditAction action,
        AuditResource resourceType,
        String resourceId,
        AuditOutcome outcome,
        Instant occurredFrom,
        Instant occurredTo,
        SortField sortField,
        SortDirection sortDirection
) {
    public enum SortField {OCCURRED_AT, ACTION, RESOURCE_TYPE, OUTCOME}

    public enum SortDirection {ASC, DESC}
}