package com.elyadata.feature.monitoring.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PerformanceDto {

    private PerformanceDto() {}

    public record Summary(
            double throughputPagesPerMin,
            double recordsPerMin,
            double avgCrawlSpeedPagesPerMin,
            double avgDurationMs,
            double p95DurationMs,
            double successRate,
            long totalPages,
            long totalRecords,
            long totalRuns,
            long completedRuns,
            long failedRuns,
            long partialRuns
    ) {}

    public record TimePoint(
            Instant bucket,
            long pages,
            long records,
            long runs,
            double pagesPerMin
    ) {}

    public record TimeSeries(
            String range,
            String granularity,
            List<TimePoint> current,
            List<TimePoint> previous
    ) {}

    public record EngineBreakdown(
            String engine,
            long runs,
            long pages,
            double avgCrawlSpeedPagesPerMin,
            double successRate
    ) {}

    public record RunPoint(
            UUID jobId,
            String jobName,
            String engine,
            String status,
            long pages,
            long records,
            long durationMs,
            double pagesPerMin,
            Instant scrapedAt
    ) {}
}