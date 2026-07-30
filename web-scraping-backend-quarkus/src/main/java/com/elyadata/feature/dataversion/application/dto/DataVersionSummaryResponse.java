package com.elyadata.feature.dataversion.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public record DataVersionSummaryResponse(
        UUID id,
        UUID scrapeResultId,
        int versionNumber,
        UUID parentVersionId,
        List<Map<String, Object>> appliedSteps,
        int recordCount,
        String label,
        UUID createdBy,
        Instant createdAt
) {
}
