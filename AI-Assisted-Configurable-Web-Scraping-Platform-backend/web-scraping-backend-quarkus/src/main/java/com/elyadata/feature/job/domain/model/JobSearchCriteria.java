package com.elyadata.feature.job.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;


@Builder(toBuilder = true)
public record JobSearchCriteria(
        UUID ownerId,
        UUID projectId,
        String search,
        JobStatus status,
        JobPriority priority,
        JobTriggerType triggerType,
        JobType jobType,
        Instant createdFrom,
        Instant createdTo,
        SortField sortField,
        SortDirection sortDirection
) {
    public enum SortField {CREATED_AT, UPDATED_AT, NAME}

    public enum SortDirection {ASC, DESC}

    public JobSearchCriteria {
        if (sortField == null) sortField = SortField.CREATED_AT;
        if (sortDirection == null) sortDirection = SortDirection.DESC;
    }
}