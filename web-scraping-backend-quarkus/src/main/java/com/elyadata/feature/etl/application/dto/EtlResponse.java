package com.elyadata.feature.etl.application.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EtlResponse(
        String scrapeResultId,
        List<Map<String, Object>> records,
        List<StepReportDto> stepReports,
        int rawCount,
        int cleanCount,
        int removedCount,
        long durationMs,
        UUID savedVersionId,
        Integer savedVersionNumber,
        QualityReportDto qualityReport
) {
}