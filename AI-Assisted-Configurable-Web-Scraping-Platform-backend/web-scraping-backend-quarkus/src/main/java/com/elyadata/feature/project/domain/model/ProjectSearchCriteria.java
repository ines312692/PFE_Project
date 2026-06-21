package com.elyadata.feature.project.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record ProjectSearchCriteria(
        UUID memberId,
        String search,
        ProjectStatus status,
        Instant createdFrom,
        Instant createdTo,
        SortField sortField,
        SortDirection sortDirection
) {
    public enum SortField {CREATED_AT, UPDATED_AT, NAME}

    public enum SortDirection {ASC, DESC}

    public ProjectSearchCriteria {
        if (sortField == null) sortField = SortField.CREATED_AT;
        if (sortDirection == null) sortDirection = SortDirection.DESC;
    }
}