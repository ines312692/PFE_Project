package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class LlmExtractor implements BaseExtractor {

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.LLM;
    }

    @Override
    public ExtractionOutput extract(PageSnapshot snapshot, InternalScrapeRequest request) {
        if (snapshot.getText() == null && snapshot.getHtml() == null) {
            log.warn("[LLM] No page content available");
            return ExtractionOutput.empty();
        }

        String content = snapshot.getText() != null && !snapshot.getText().isBlank()
                ? snapshot.getText().strip()
                : snapshot.getHtml();

        log.info("[LLM] Raw content captured ({} chars) — forwarding to Groq", content.length());
        return ExtractionOutput.raw(content);
    }
}