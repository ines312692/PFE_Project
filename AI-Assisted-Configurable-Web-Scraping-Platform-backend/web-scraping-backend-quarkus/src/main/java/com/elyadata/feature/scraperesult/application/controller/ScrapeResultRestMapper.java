package com.elyadata.feature.scraperesult.application.controller;

import com.elyadata.feature.scraperesult.application.dto.ScrapeResultResponse;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ScrapeResultRestMapper {

    public ScrapeResultResponse toResponse(ScrapeResult r) {
        return new ScrapeResultResponse(
                r.id(),
                r.jobId(),
                r.status(),
                r.records(),
                r.errors(),
                r.logs(),
                r.pagesCrawled(),
                r.durationMs(),
                r.engineUsed(),
                r.scrapedAt() != null ? r.scrapedAt().toString() : null,
                r.discoveredFiles() != null ? r.discoveredFiles() : List.of()
        );
    }
}