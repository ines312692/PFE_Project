package com.elyadata.feature.scraperesult.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder(toBuilder = true)
public record ScrapeResult(
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
) {}