package com.elyadata.feature.scraper.browser;

import com.elyadata.feature.job.domain.service.port.PageRendererPort;
import com.elyadata.feature.scraper.browser.humanizer.Humanizer;
import com.elyadata.feature.scraper.browser.humanizer.HumanizerLevel;
import com.elyadata.feature.scraper.browser.llm.ActionSelectorResolver;
import com.elyadata.feature.scraper.browser.login.LoginStrategyRegistry;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfile;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfileFactory;
import com.elyadata.feature.scraper.browser.stealth.StealthProfile;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlaywrightPageRenderer implements PageRendererPort {

    private static final int RENDER_TIMEOUT_MS = 45_000;

    private final StealthProfile stealthProfile;
    private final ConsistencyProfileFactory consistencyProfileFactory;
    private final Humanizer humanizer;
    private final LoginStrategyRegistry loginStrategyRegistry;
    private final ActionSelectorResolver actionSelectorResolver;

    @Override
    public Optional<String> renderHtml(String url, boolean stealth) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        InternalScrapeRequest req = InternalScrapeRequest.builder()
                .targetUrl(url)
                .jsEnabled(true)
                .stealthMode(stealth)
                .timeoutMs(RENDER_TIMEOUT_MS)
                .singlePageMode(true)
                .build();

        ConsistencyProfile profile = consistencyProfileFactory.buildFor(null);

        ABrowser browser = new PlaywrightBrowser(
                req, stealthProfile, profile, humanizer, HumanizerLevel.REALISTIC,
                loginStrategyRegistry, actionSelectorResolver, null);

        try {
            browser.start();
            browser.navigate(url);
            browser.dismissCookies();
            String html = browser.snapshot().getHtml();

            if (html == null || html.isBlank()) {
                log.warn("[PAGE-RENDER] Empty HTML for {}", url);
                return Optional.empty();
            }
            log.info("[PAGE-RENDER] Rendered {} chars from {} (stealth={})",
                    html.length(), url, stealth);
            return Optional.of(html);

        } catch (Exception e) {
            log.warn("[PAGE-RENDER] Render failed for {} ({})", url, e.getMessage());
            return Optional.empty();
        } finally {
            try {
                browser.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }
}