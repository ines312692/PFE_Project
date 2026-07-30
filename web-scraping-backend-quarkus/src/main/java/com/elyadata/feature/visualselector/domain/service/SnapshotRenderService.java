package com.elyadata.feature.visualselector.domain.service;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.browser.PlaywrightBrowser;
import com.elyadata.feature.scraper.browser.humanizer.Humanizer;
import com.elyadata.feature.scraper.browser.humanizer.HumanizerLevel;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfile;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfileFactory;
import com.elyadata.feature.scraper.browser.stealth.StealthProfile;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.visualselector.application.dto.SnapshotRequest;
import com.elyadata.feature.visualselector.application.dto.SnapshotResponse;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;

import java.util.Set;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SnapshotRenderService {

    private static final int RENDER_TIMEOUT_MS = 60_000;
    private static final int MIN_USABLE_HTML = 500;

    private final StealthProfile stealthProfile;
    private final ConsistencyProfileFactory consistencyProfileFactory;
    private final Humanizer humanizer;
    private final HtmlSanitizer sanitizer;

    public SnapshotResponse render(SnapshotRequest request) {
        InternalScrapeRequest internal = buildInternalRequest(request);
        ConsistencyProfile profile = consistencyProfileFactory.buildFor(null);

        ABrowser browser = new PlaywrightBrowser(
                internal, stealthProfile, profile, humanizer, HumanizerLevel.REALISTIC);

        browser.start();
        try {
            browser.navigate(request.url());
            browser.dismissCookies();
            if (request.waitForSelector() != null && !request.waitForSelector().isBlank()) {
                browser.waitForSelector(request.waitForSelector());
            }

            PageSnapshot snapshot = browser.snapshot();
            String rawHtml = snapshot.getHtml();

            if (rawHtml == null || rawHtml.length() < MIN_USABLE_HTML) {
                throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                        .message("Rendered page too small (" + (rawHtml == null ? 0 : rawHtml.length())
                                + " chars) — the site may be blocking the request. Try enabling stealth mode.")
                        .uriPath("visual-selector/snapshot")
                        .subErrors(Set.of())
                        .build();
            }

            String finalUrl = snapshot.getUrl() != null ? snapshot.getUrl() : request.url();
            String sanitised = sanitizer.sanitize(rawHtml, finalUrl);

            log.info("[VISUAL] Snapshot rendered url={} finalUrl={} raw={}b sanitised={}b stealth={}",
                    request.url(), finalUrl, rawHtml.length(), sanitised.length(), request.stealthMode());

            return SnapshotResponse.builder()
                    .finalUrl(finalUrl)
                    .html(sanitised)
                    .title(extractTitle(rawHtml))
                    .htmlLength(sanitised.length())
                    .build();

        } catch (Infrastructure5xxException e) {
            throw e;
        } catch (Exception e) {
            log.error("[VISUAL] Snapshot render failed url={}: {}", request.url(), e.getMessage(), e);
            throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                    .message("Failed to render snapshot for " + request.url() + ": " + e.getMessage())
                    .uriPath("visual-selector/snapshot")
                    .subErrors(Set.of())
                    .build();
        } finally {
            browser.close();
        }
    }

    private InternalScrapeRequest buildInternalRequest(SnapshotRequest request) {
        return InternalScrapeRequest.builder()
                .targetUrl(request.url())
                .jsEnabled(true)
                .stealthMode(request.stealthMode())
                .timeoutMs(RENDER_TIMEOUT_MS)
                .delayMs(0)
                .scrollToBottom(false)
                .build();
    }

    private String extractTitle(String rawHtml) {
        try {
            String title = Jsoup.parse(rawHtml).title();
            return !title.isBlank() ? title : "Untitled page";
        } catch (Exception e) {
            return "Untitled page";
        }
    }
}