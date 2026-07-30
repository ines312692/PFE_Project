package com.elyadata.feature.scraper.browser;

import com.elyadata.feature.job.domain.model.SessionConfig;
import com.elyadata.feature.scraper.browser.humanizer.Humanizer;
import com.elyadata.feature.scraper.browser.humanizer.HumanizerLevel;
import com.elyadata.feature.scraper.browser.llm.ActionSelectorResolver;
import com.elyadata.feature.scraper.browser.login.LoginStrategyRegistry;
import com.elyadata.feature.scraper.browser.pool.BrowserPool;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfile;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfileFactory;
import com.elyadata.feature.scraper.browser.stealth.StealthProfile;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class BrowserFactory {

    private final StealthProfile stealthProfile;
    private final ConsistencyProfileFactory consistencyProfileFactory;
    private final Humanizer humanizer;
    private final LoginStrategyRegistry loginStrategyRegistry;
    private final ActionSelectorResolver actionSelectorResolver;
    private final BrowserPool browserPool;

    public ABrowser create(InternalScrapeRequest request) {
        InternalScrapeRequest prepared = withEngineDefaults(request);

        log.info("[BROWSER-FACTORY] PlaywrightBrowser (stealth={} proxy={} isolated={})",
                prepared.isStealthMode(), prepared.getProxy() != null,
                prepared.isIsolatedBrowser());

        ConsistencyProfile profile = consistencyProfileFactory.buildFor(prepared.getProxy());
        String seed = resolveSeedState(prepared);

        return new PlaywrightBrowser(prepared, stealthProfile, profile,
                humanizer, HumanizerLevel.REALISTIC, loginStrategyRegistry,
                actionSelectorResolver, seed, browserPool);
    }

    private String resolveSeedState(InternalScrapeRequest request) {
        if (request.getRestoredStorageState() != null
                && !request.getRestoredStorageState().isBlank()) {
            return request.getRestoredStorageState();
        }
        SessionConfig sc = request.getSessionConfig();
        if (sc != null && sc.isUsable()
                && sc.mode() == SessionConfig.LoginMode.COOKIE_IMPORT) {
            return sc.importedStorageState();
        }
        return null;
    }

    private static InternalScrapeRequest withEngineDefaults(InternalScrapeRequest req) {
        return req.toBuilder()
                .jsEnabled(true)
                .timeoutMs(Math.max(90_000, req.getTimeoutMs()))
                .isolatedBrowser(resolveIsolation(req))
                .build();
    }

    private static boolean resolveIsolation(InternalScrapeRequest req) {
        if (req.getSessionConfig() != null && req.getSessionConfig().isUsable()) {
            return true;
        }
        if (req.getRestoredStorageState() != null
                && !req.getRestoredStorageState().isBlank()) {
            return true;
        }
        return req.isIsolatedBrowser();
    }
}