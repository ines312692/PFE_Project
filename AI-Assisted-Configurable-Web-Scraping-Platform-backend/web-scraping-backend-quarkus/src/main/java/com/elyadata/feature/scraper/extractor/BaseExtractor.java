package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;

public interface BaseExtractor {

    ExtractionMode mode();

    ExtractionOutput extract(PageSnapshot snapshot, InternalScrapeRequest request);
}