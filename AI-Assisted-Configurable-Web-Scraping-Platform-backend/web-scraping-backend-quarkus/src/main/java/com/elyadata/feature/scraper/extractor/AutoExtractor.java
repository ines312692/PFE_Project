package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AutoExtractor implements BaseExtractor {

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.AUTO;
    }

    @Override
    public ExtractionOutput extract(PageSnapshot snapshot, InternalScrapeRequest request) {
        String html = snapshot.getHtml();
        if (html == null || html.isBlank()) {
            return ExtractionOutput.empty();
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("url", snapshot.getUrl());
        entry.put("html", html);
        entry.put("html_length", html.length());

        return ExtractionOutput.of(List.of(entry));
    }
}