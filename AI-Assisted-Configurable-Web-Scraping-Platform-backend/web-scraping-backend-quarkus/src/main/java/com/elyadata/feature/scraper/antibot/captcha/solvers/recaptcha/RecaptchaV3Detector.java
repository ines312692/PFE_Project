package com.elyadata.feature.scraper.antibot.captcha.solvers.recaptcha;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class RecaptchaV3Detector {

    private static final String DETECT_JS = """
            () => {
              // 1) Script tag canonique v3 : api.js?render=SITEKEY
              const scripts = Array.from(document.querySelectorAll('script[src*="recaptcha/api.js"]'));
              for (const s of scripts) {
                const m = s.src.match(/[?&]render=([^&]+)/);
                if (m && m[1] && m[1] !== 'explicit') {
                  return { siteKey: m[1], source: 'script-render' };
                }
              }
              // 2) grecaptcha.execute() exposé mais aucun widget v2 visible
              if (window.grecaptcha
                  && typeof window.grecaptcha.execute === 'function'
                  && !document.querySelector('.g-recaptcha[data-sitekey]')
                  && !document.querySelector('iframe[src*="recaptcha/api2/anchor"]')) {
                const el = document.querySelector('[data-sitekey]');
                if (el) return { siteKey: el.getAttribute('data-sitekey'), source: 'data-sitekey' };
              }
              return null;
            }
            """;

    private static final String DETECT_ACTION_JS = """
            () => {
              // Pattern 1: grecaptcha.execute(key, {action: 'X'})
              const html = document.documentElement.outerHTML;
              const m1 = html.match(/grecaptcha\\.execute\\([^)]*action\\s*:\\s*['"]([\\w-]+)['"]/);
              if (m1) return m1[1];
              // Pattern 2: action inversé dans la config
              const m2 = html.match(/['"]action['"]\\s*:\\s*['"]([\\w-]+)['"][^}]*}\\s*\\)/);
              if (m2) return m2[1];
              // Pattern 3: scripts inline
              for (const s of document.querySelectorAll('script:not([src])')) {
                const m = s.textContent.match(/action\\s*:\\s*['"]([\\w-]+)['"]/);
                if (m) return m[1];
              }
              return null;
            }
            """;

    @ConfigProperty(name = "captcha.v3.default-min-score", defaultValue = "0.5")
    double defaultMinScore;

    @ConfigProperty(name = "captcha.v3.default-action", defaultValue = "submit")
    String defaultAction;

    public Optional<CaptchaChallenge> detect(PageSnapshot snapshot, UUID jobId) {
        Page page = extractPage(snapshot);
        if (page == null) return Optional.empty();

        try {
            Object raw = page.evaluate(DETECT_JS);
            if (raw == null) return Optional.empty();

            Map<String, Object> info = (Map<String, Object>) raw;
            String siteKey = (String) info.get("siteKey");
            String source = (String) info.get("source");
            if (siteKey == null || siteKey.isBlank()) return Optional.empty();

            String action = defaultAction;
            try {
                Object actionRaw = page.evaluate(DETECT_ACTION_JS);
                if (actionRaw != null && !actionRaw.toString().isBlank()) {
                    action = actionRaw.toString();
                }
            } catch (Exception ignored) { }

            log.info("[CAPTCHA-V3-DETECT] Found v3 siteKey={} action={} source={} job={}",
                    mask(siteKey), action, source, jobId);

            return Optional.of(CaptchaChallenge.builder()
                    .type(CaptchaType.RECAPTCHA_V3)
                    .siteKey(siteKey)
                    .pageUrl(snapshot.getUrl())
                    .jobId(jobId)
                    .action(action)
                    .minScore(defaultMinScore)
                    .build());

        } catch (Exception e) {
            log.warn("[CAPTCHA-V3-DETECT] Detection failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Page extractPage(PageSnapshot snapshot) {
        if (snapshot == null) return null;
        LiveBrowserContext ctx = snapshot.getBrowserContext();
        return ctx != null && ctx.isPlaywright() ? ctx.getPage() : null;
    }

    private static String mask(String s) {
        return s == null || s.length() < 10 ? "?" : s.substring(0, 6) + "...";
    }
}