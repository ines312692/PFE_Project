package com.elyadata.feature.scraper.browser.stealth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PlaywrightStealthProfile implements StealthProfile {

    private final StealthScriptLoader scriptLoader;

    @ConfigProperty(name = "scraper.browser.stealth-level", defaultValue = "AGGRESSIVE")
    StealthLevel stealthLevel;

    @Override
    public void prepareContextOptions(Browser.NewContextOptions options, ConsistencyProfile p) {
        if (stealthLevel == StealthLevel.NONE) {
            options.setViewportSize(p.viewportWidth(), p.viewportHeight());
            log.warn("[STEALTH] level=NONE — default Playwright fingerprint, " +
                    "bot-detectable (testing/debugging only)");
            return;
        }

        options
                .setUserAgent(p.userAgent())
                .setViewportSize(p.viewportWidth(), p.viewportHeight())
                .setLocale(p.locale())
                .setTimezoneId(p.timezone())
                .setExtraHTTPHeaders(Map.of("Accept-Language", p.acceptLanguage()));
        log.debug("[STEALTH] Context options prepared — ua={}... viewport={}x{} locale={} tz={}",
                p.userAgent().substring(0, Math.min(60, p.userAgent().length())),
                p.viewportWidth(), p.viewportHeight(), p.locale(), p.timezone());
    }

    @Override
    public void applyInitScripts(BrowserContext context, ConsistencyProfile profile) {
        if (stealthLevel == StealthLevel.NONE) {
            log.warn("[STEALTH] level=NONE — skipping all init scripts");
            return;
        }

        List<String> scripts = scriptLoader.resolveScripts(stealthLevel, profile);
        for (String script : scripts) {
            context.addInitScript(script);
        }
        log.info("[STEALTH] Applied {} init scripts (level={})", scripts.size(), stealthLevel);
    }
}