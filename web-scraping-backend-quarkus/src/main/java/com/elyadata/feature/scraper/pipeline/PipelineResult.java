package com.elyadata.feature.scraper.pipeline;

import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;

import java.util.List;
import java.util.Map;

public record PipelineResult(
        String status,
        List<Map<String, Object>> records,
        List<String> errors,
        List<String> logs,
        int pagesCrawled,
        long durationMs,
        String engineUsed,
        String rawContent,
        List<DiscoveredFile> discoveredFiles
) {
}