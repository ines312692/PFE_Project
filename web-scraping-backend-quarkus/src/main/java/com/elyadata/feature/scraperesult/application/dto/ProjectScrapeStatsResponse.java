package com.elyadata.feature.scraperesult.application.dto;

import java.util.List;

public record ProjectScrapeStatsResponse(
        long totalRuns,
        long completedRuns,
        long failedRuns,
        long partialRuns,
        double successRate,
        long totalPagesCrawled,
        long totalRecords,
        long totalFilesDiscovered,
        long avgDurationMs,
        List<DailyPoint> trend
) {
    public record DailyPoint(String date, long pages) {}
}