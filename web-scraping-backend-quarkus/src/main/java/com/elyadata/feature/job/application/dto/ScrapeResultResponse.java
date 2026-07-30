package com.elyadata.feature.job.application.dto;

import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ScrapeResultResponse(
        UUID id,
        UUID jobId,
        String status,
        List<Map<String, Object>> records,
        List<String> errors,
        List<String> logs,
        int pagesCrawled,
        long durationMs,
        String engineUsed,
        Instant scrapedAt,
        List<DiscoveredFile> discoveredFiles
) {
}