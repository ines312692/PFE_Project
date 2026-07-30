package com.elyadata.feature.scraperesult.application.dto;

import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        String scrapedAt,
        List<DiscoveredFile> discoveredFiles
) {}