package com.elyadata.feature.scraperesult.domain.service.port;

import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScrapeResultPort {
    ScrapeResult save(ScrapeResult result);

    Optional<ScrapeResult> findByJobId(UUID jobId);

    List<ScrapeResult> findAllByJobId(UUID jobId);

    Optional<ScrapeResult> findById(UUID id);
}