package com.elyadata.feature.dataversion.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Builder(toBuilder = true)
public record DataVersion(
        UUID id,
        UUID scrapeResultId,
        int versionNumber,
        UUID parentVersionId,
        List<Map<String, Object>> records,
        List<Map<String, Object>> appliedSteps,
        int recordCount,
        String label,
        UUID createdBy,
        Instant createdAt
) {


    public static final int RAW_VERSION = 0;

    public boolean isRaw() {
        return versionNumber == RAW_VERSION;
    }
}