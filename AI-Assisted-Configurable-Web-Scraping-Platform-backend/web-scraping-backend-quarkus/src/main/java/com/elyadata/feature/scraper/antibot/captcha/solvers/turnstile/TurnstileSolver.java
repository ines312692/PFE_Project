package com.elyadata.feature.scraper.antibot.captcha.solvers.turnstile;

import com.elyadata.feature.scraper.browser.humanizer.motion.BezierMouseMotion;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TurnstileSolver {

    private final TurnstileDetector detector;
    private final TurnstileMatcher matcher;
    private final TurnstileObserver observer;
    private final BezierMouseMotion mouseMotion;

    private static final int DETECT_TIMEOUT_MS = 12_000;
    private static final int AUTO_RESOLVE_MS = 20_000;
    private static final int POST_CLICK_WAIT_MS = 10_000;
    private static final int TOTAL_BUDGET_MS = 60_000;
    private static final int POLL_MS = 600;
    private static final int MIN_TOKEN_LENGTH = 20;
    private static final int MAX_CLICK_ATTEMPTS = 4;

    private static final double CHECKBOX_WIDTH_PX = 24;
    private static final double CHECKBOX_LEFT_OFFSET = 30;

    public static final String CLEARED_NO_TOKEN_SENTINEL = "__cleared_no_token__";

    private static final String TOKEN_PROBE_JS = """
            () => {
              const primary = document.querySelector('input[name="cf-turnstile-response"]');
              if (primary && primary.value && primary.value.length > 20) return primary.value;
              try {
                if (window.turnstile && typeof window.turnstile.getResponse === 'function') {
                  const t = window.turnstile.getResponse();
                  if (t && t.length > 20) return t;
                }
              } catch (e) {}
              for (const i of document.querySelectorAll('input[type="hidden"]')) {
                if (i.name && i.name.toLowerCase().includes('turnstile')
                    && i.value && i.value.length > 20) return i.value;
              }
              return null;
            }
            """;

    private static final String CLEARED_PROBE_JS = """
            () => {
              const stillCf = !!document.querySelector(
                'iframe[src*="challenges.cloudflare.com"], [id^="cf-chl-widget-"], ' +
                '.cf-turnstile[data-sitekey], #challenge-stage, #challenge-form'
              );
              const bodyLen = (document.body && document.body.innerText || '').length;
              return { stillCf, bodyLen };
            }
            """;

    private static final String DOM_DUMP_JS = """
            () => {
              const iframes = Array.from(document.querySelectorAll('iframe')).map(f => ({
                src: f.src ? f.src.substring(0, 150) : '(empty)',
                w: f.offsetWidth, h: f.offsetHeight,
                visible: f.offsetParent !== null,
                rect: f.getBoundingClientRect ? {
                  x: Math.round(f.getBoundingClientRect().x),
                  y: Math.round(f.getBoundingClientRect().y),
                  w: Math.round(f.getBoundingClientRect().width),
                  h: Math.round(f.getBoundingClientRect().height)
                } : null
              }));
              const cfWrappers = Array.from(document.querySelectorAll(
                '[id^="cf-chl-widget-"], .cf-turnstile, [data-sitekey], ' +
                '#challenge-stage, #challenge-form, #challenge-running'
              )).map(el => ({
                tag: el.tagName.toLowerCase(),
                id: el.id || null,
                cls: el.className || null,
                sitekey: el.getAttribute('data-sitekey') || null,
                w: el.offsetWidth, h: el.offsetHeight,
                visible: el.offsetParent !== null
              }));
              const bodyText = (document.body && document.body.innerText || '').substring(0, 400);
              return {
                url: window.location.href,
                title: document.title,
                bodyLen: (document.body && document.body.innerText || '').length,
                bodyTextSample: bodyText,
                iframes: iframes,
                cfWrappers: cfWrappers
              };
            }
            """;

    public Optional<String> solve(Page page, UUID jobId) {
        long startMs = System.currentTimeMillis();
        long deadline = startMs + TOTAL_BUDGET_MS;
        log.info("[TURNSTILE] start job={}", jobId);

        TurnstileDetector.TurnstileType type = pollDetect(page);
        if (type == TurnstileDetector.TurnstileType.NONE) {
            log.warn("[TURNSTILE] no widget detected job={}", jobId);
            dumpDomDiagnostic(page, jobId, "no_widget_detected");
            return Optional.empty();
        }
        log.info("[TURNSTILE] type={} job={}", type, jobId);

        observer.install(page, type.name().toLowerCase(), TOTAL_BUDGET_MS);
        sleepMs(1200 + (long) ThreadLocalRandom.current().nextInt(0, 900));

        if (isInterstitialCleared(page)) {
            log.info("[TURNSTILE] cleared without action (good fingerprint) job={}", jobId);
            return Optional.of(CLEARED_NO_TOKEN_SENTINEL);
        }

        log.info("[TURNSTILE] waiting up to {}ms for silent resolve job={}", AUTO_RESOLVE_MS, jobId);
        long autoResolveEnd = System.currentTimeMillis() + AUTO_RESOLVE_MS;
        while (System.currentTimeMillis() < autoResolveEnd) {
            if (isInterstitialCleared(page)) {
                log.info("[TURNSTILE] cleared during silent poll (elapsed={}ms) job={}",
                        System.currentTimeMillis() - startMs, jobId);
                return Optional.of(CLEARED_NO_TOKEN_SENTINEL);
            }
            Optional<String> t = pollForToken(page, 500);
            if (t.isPresent()) {
                log.info("[TURNSTILE] auto-resolved silently len={} job={}", t.get().length(), jobId);
                return t;
            }
        }

        log.info("[TURNSTILE] silent resolve failed — entering click loop job={}", jobId);

        int attempts = 0;
        while (System.currentTimeMillis() < deadline && attempts < MAX_CLICK_ATTEMPTS) {
            if (isInterstitialCleared(page)) {
                log.info("[TURNSTILE] cleared between attempts job={}", jobId);
                return Optional.of(CLEARED_NO_TOKEN_SENTINEL);
            }

            if (observer.isVerified(page)) {
                Optional<String> verifiedToken = pollForToken(page, 2_000);
                if (verifiedToken.isPresent()) {
                    log.info("[TURNSTILE] verified-without-click len={} job={}",
                            verifiedToken.get().length(), jobId);
                    return verifiedToken;
                }
                if (isInterstitialCleared(page)) {
                    return Optional.of(CLEARED_NO_TOKEN_SENTINEL);
                }
            }

            attempts++;
            log.info("[TURNSTILE] click attempt {}/{} job={}", attempts, MAX_CLICK_ATTEMPTS, jobId);

            boolean clicked = clickByTemplateMatch(page, jobId)
                    || clickByWidgetBbox(page, jobId)
                    || clickByIframeBbox(page, jobId)
                    || clickByIframeCenter(page, jobId);

            if (!clicked) {
                log.info("[TURNSTILE] no clickable target found attempt={} job={}", attempts, jobId);
                sleepMs(POLL_MS);
                continue;
            }

            long postClickEnd = System.currentTimeMillis() + POST_CLICK_WAIT_MS;
            while (System.currentTimeMillis() < postClickEnd) {
                if (isInterstitialCleared(page)) {
                    log.info("[TURNSTILE] cleared after click attempt={} (elapsed={}ms) job={}",
                            attempts, System.currentTimeMillis() - startMs, jobId);
                    return Optional.of(CLEARED_NO_TOKEN_SENTINEL);
                }
                Optional<String> postClickToken = pollForToken(page, 500);
                if (postClickToken.isPresent()) {
                    log.info("[TURNSTILE] solved attempt={} len={} job={}",
                            attempts, postClickToken.get().length(), jobId);
                    return postClickToken;
                }
            }

            sleepMs(1500 + (long) ThreadLocalRandom.current().nextInt(0, 1500));
        }

        long elapsed = System.currentTimeMillis() - startMs;
        log.warn("[TURNSTILE] gave up after {}ms attempts={} job={}", elapsed, attempts, jobId);
        dumpDomDiagnostic(page, jobId, "all_attempts_failed");
        return Optional.empty();
    }

    private boolean isInterstitialCleared(Page page) {
        try {
            boolean hasClearance = page.context().cookies().stream()
                    .anyMatch(c -> "cf_clearance".equals(c.name)
                            && c.value != null && !c.value.isBlank());
            if (hasClearance) return true;

            Object probe = page.evaluate(CLEARED_PROBE_JS);
            if (probe instanceof Map<?, ?> map) {
                Object stillCf = map.get("stillCf");
                Object bodyLen = map.get("bodyLen");
                boolean cfGone = Boolean.FALSE.equals(stillCf);
                int len = bodyLen instanceof Number n ? n.intValue() : 0;
                return cfGone && len > 2000;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    private void dumpDomDiagnostic(Page page, UUID jobId, String reason) {
        try {
            Object dump = page.evaluate(DOM_DUMP_JS);
            log.warn("[TURNSTILE-DIAG] reason={} job={} dump={}", reason, jobId, dump);
        } catch (Exception e) {
            log.warn("[TURNSTILE-DIAG] dump failed reason={} job={}: {}", reason, jobId, e.getMessage());
        }
    }

    private boolean clickByTemplateMatch(Page page, UUID jobId) {
        if (!matcher.isReady()) {
            log.info("[TURNSTILE] template-match: skipped (matcher not ready) job={}", jobId);
            return false;
        }
        try {
            byte[] png = page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(false)
                    .setType(ScreenshotType.PNG));
            Optional<Point2D> target = matcher.findCheckbox(png);
            if (target.isEmpty()) {
                log.info("[TURNSTILE] template-match: no match in screenshot job={}", jobId);
                return false;
            }
            humanClick(page, target.get().getX(), target.get().getY());
            log.info("[TURNSTILE] clicked via template-match ({}, {}) job={}",
                    (int) target.get().getX(), (int) target.get().getY(), jobId);
            return true;
        } catch (Exception e) {
            log.info("[TURNSTILE] template-match click failed: {} job={}", e.getMessage(), jobId);
            return false;
        }
    }

    private boolean clickByWidgetBbox(Page page, UUID jobId) {
        try {
            Locator widget = page.locator(
                    ".cf-turnstile[data-sitekey], " +
                            "div[data-sitekey][class*='turnstile'], " +
                            "[id^='cf-chl-widget-'], " +
                            "#turnstile-wrapper"
            ).first();
            BoundingBox bbox = widget.boundingBox();
            if (bbox == null) {
                log.info("[TURNSTILE] widget-bbox: element not found job={}", jobId);
                return false;
            }
            if (bbox.width < 50 || bbox.height < 30) {
                log.info("[TURNSTILE] widget-bbox: too small ({}x{}) — likely placeholder job={}",
                        (int) bbox.width, (int) bbox.height, jobId);
                return false;
            }
            double x = bbox.x + CHECKBOX_LEFT_OFFSET;
            double y = bbox.y + bbox.height / 2.0;
            humanClick(page, x, y);
            log.info("[TURNSTILE] clicked via widget-bbox ({}, {}) bbox={}x{} job={}",
                    (int) x, (int) y, (int) bbox.width, (int) bbox.height, jobId);
            return true;
        } catch (Exception e) {
            log.info("[TURNSTILE] widget-bbox click failed: {} job={}", e.getMessage(), jobId);
            return false;
        }
    }

    private boolean clickByIframeBbox(Page page, UUID jobId) {
        try {
            Locator iframe = page.locator("iframe[src*='challenges.cloudflare.com']").first();
            iframe.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED)
                    .setTimeout(3_000));
            BoundingBox bbox = iframe.boundingBox();
            if (bbox == null) {
                log.info("[TURNSTILE] iframe-bbox: bbox null job={}", jobId);
                return false;
            }
            if (bbox.width < 50) {
                log.info("[TURNSTILE] iframe-bbox: too narrow ({}x{}) job={}",
                        (int) bbox.width, (int) bbox.height, jobId);
                return false;
            }
            double x = bbox.x + CHECKBOX_LEFT_OFFSET;
            double y = bbox.y + bbox.height / 2.0;
            humanClick(page, x, y);
            log.info("[TURNSTILE] clicked via iframe-bbox ({}, {}) bbox={}x{} job={}",
                    (int) x, (int) y, (int) bbox.width, (int) bbox.height, jobId);
            return true;
        } catch (Exception e) {
            log.info("[TURNSTILE] iframe-bbox click failed: {} job={}", e.getMessage(), jobId);
            return false;
        }
    }

    private boolean clickByIframeCenter(Page page, UUID jobId) {
        try {
            Locator iframe = page.locator("iframe[src*='challenges.cloudflare.com']").first();
            iframe.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED)
                    .setTimeout(3_000));
            BoundingBox bbox = iframe.boundingBox();
            if (bbox == null || bbox.width < 50) {
                log.info("[TURNSTILE] iframe-center: bbox null/small job={}", jobId);
                return false;
            }
            double x = bbox.x + bbox.width / 2.0;
            double y = bbox.y + bbox.height / 2.0;
            humanClick(page, x, y);
            log.info("[TURNSTILE] clicked via iframe-center ({}, {}) bbox={}x{} job={}",
                    (int) x, (int) y, (int) bbox.width, (int) bbox.height, jobId);
            return true;
        } catch (Exception e) {
            log.info("[TURNSTILE] iframe-center click failed: {} job={}", e.getMessage(), jobId);
            return false;
        }
    }

    private void humanClick(Page page, double targetX, double targetY) {
        if (ThreadLocalRandom.current().nextDouble() < 0.4) {
            mouseMotion.idleMicroMovements(page, 1 + ThreadLocalRandom.current().nextInt(2));
        }

        mouseMotion.moveTo(page, targetX, targetY, CHECKBOX_WIDTH_PX);

        sleepMs(140 + (long) ThreadLocalRandom.current().nextInt(0, 220));
        page.mouse().down();
        sleepMs(45 + (long) ThreadLocalRandom.current().nextInt(0, 90));
        page.mouse().up();
    }

    private TurnstileDetector.TurnstileType pollDetect(Page page) {
        long end = System.currentTimeMillis() + DETECT_TIMEOUT_MS;
        while (System.currentTimeMillis() < end) {
            TurnstileDetector.TurnstileType t = detector.detect(page);
            if (t != TurnstileDetector.TurnstileType.NONE) return t;
            sleepMs(400);
        }
        return TurnstileDetector.TurnstileType.NONE;
    }

    private Optional<String> pollForToken(Page page, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            try {
                Object raw = page.evaluate(TOKEN_PROBE_JS);
                if (raw != null) {
                    String t = raw.toString().trim();
                    if (t.length() >= MIN_TOKEN_LENGTH) return Optional.of(t);
                }
            } catch (Exception ignored) {
                // ignore
            }
            sleepMs(POLL_MS);
        }
        return Optional.empty();
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}