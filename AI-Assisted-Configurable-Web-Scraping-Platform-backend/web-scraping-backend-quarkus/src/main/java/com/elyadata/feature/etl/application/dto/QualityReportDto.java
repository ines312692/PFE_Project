package com.elyadata.feature.etl.application.dto;

import com.elyadata.feature.etl.domain.model.FieldQuality;
import com.elyadata.feature.etl.domain.model.QualityReport;
import com.elyadata.feature.etl.domain.model.RuleViolation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record QualityReportDto(
        int totalRecords,
        int validRecords,
        int invalidRecords,
        double validityRate,
        Map<String, FieldQualityDto> byField,
        List<RuleViolationDto> topViolations,
        long durationMs
) {

    public static QualityReportDto from(QualityReport r) {
        if (r == null) return null;
        Map<String, FieldQualityDto> fields = r.byField().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> FieldQualityDto.from(e.getValue()),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
        List<RuleViolationDto> violations = r.topViolations().stream()
                .map(RuleViolationDto::from)
                .toList();
        return new QualityReportDto(r.totalRecords(), r.validRecords(),
                r.invalidRecords(), r.validityRate(), fields, violations, r.durationMs());
    }

    public record FieldQualityDto(
            String field,
            int totalChecks,
            int passed,
            int failed,
            int missing,
            double completenessRate,
            double validityRate,
            Map<String, Integer> failuresByRule
    ) {
        static FieldQualityDto from(FieldQuality f) {
            return new FieldQualityDto(f.field(), f.totalChecks(), f.passed(),
                    f.failed(), f.missing(), f.completenessRate(),
                    f.validityRate(), f.failuresByRule());
        }
    }

    public record RuleViolationDto(
            int recordIndex,
            String field,
            String rule,
            Object actualValue,
            String message
    ) {
        static RuleViolationDto from(RuleViolation v) {
            return new RuleViolationDto(v.recordIndex(), v.field(), v.rule(),
                    v.actualValue(), v.message());
        }
    }
}