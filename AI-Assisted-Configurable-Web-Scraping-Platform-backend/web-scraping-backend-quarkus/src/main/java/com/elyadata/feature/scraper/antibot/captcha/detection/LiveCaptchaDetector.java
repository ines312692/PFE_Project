package com.elyadata.feature.scraper.antibot.captcha.detection;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class LiveCaptchaDetector {

    private static final String DETECTION_SCRIPT = """
             () => {
               const arkose = document.querySelector(
                 'iframe[src*="arkoselabs.com"], iframe[src*="funcaptcha.com"], ' +
                 'iframe[id*="arkose"], iframe[id*="FunCaptcha"], iframe[data-e2e*="enforcement"]');
               if (arkose) {
                 const pk = document.querySelector('[data-pkey]');
                 return 'FUNCAPTCHA|' + (pk ? pk.getAttribute('data-pkey') : 'arkose');
               }

               const turn = document.querySelector('.cf-turnstile, [data-sitekey].cf-turnstile');
               if (turn) {
                 const key = turn.getAttribute('data-sitekey');
                 if (key) return 'TURNSTILE|' + key;
               }

               const v3Scripts = Array.from(document.querySelectorAll('script[src*="recaptcha/api.js"]'));
               for (const s of v3Scripts) {
                 const m = s.src.match(/[?&]render=([^&]+)/);
                 if (m && m[1] && m[1] !== 'explicit') {
                   const hasV2Widget = !!document.querySelector('.g-recaptcha[data-sitekey]');
                   if (!hasV2Widget) {
                     return 'RECAPTCHA_V3|' + m[1];
                   }
                 }
               }

               const hcapDiv = document.querySelector('.h-captcha[data-sitekey]');
               if (hcapDiv) {
                 const key = hcapDiv.getAttribute('data-sitekey');
                 if (key) return 'HCAPTCHA|' + key;
               }
               const hcapIframe = document.querySelector('iframe[src*="hcaptcha.com"]');
               if (hcapIframe) {
                 const params = new URLSearchParams(hcapIframe.src.split('?')[1] || '');
                 const key = params.get('sitekey');
                 if (key) return 'HCAPTCHA|' + key;
               }

               const recDiv = document.querySelector('.g-recaptcha[data-sitekey]');
               if (recDiv) {
                 const dk = recDiv.getAttribute('data-sitekey');
                 if (dk) return 'RECAPTCHA_V2|' + dk;
               }
               const recIframe = document.querySelector('iframe[src*="recaptcha/api2/anchor"]');
               if (recIframe) {
                 const params = new URLSearchParams(recIframe.src.split('?')[1] || '');
                 const key = params.get('k');
                 if (key) return 'RECAPTCHA_V2|' + key;
               }

               const geetest = document.querySelector(
                 '.geetest_btn, .geetest_radar_tip, .geetest_window, [class*="geetest_"]');
               if (geetest) {
                 return 'GEETEST|geetest-no-sitekey';
               }

               const rotate = document.querySelector(
                 "[class*='rotate-captcha'], [class*='rotatecaptcha'], " +
                 "[id*='rotate-captcha'], [data-captcha-type='rotate']");
               if (rotate) {
                 return 'ROTATE|rotate-no-sitekey';
               }

               return null;
             }
            """;

    private static final String CF_CHALLENGE_PROBE = """
            () => !!document.querySelector(
              'iframe[src*="challenges.cloudflare.com"], ' +
              '[id^="cf-chl-widget-"], ' +
              '#challenge-form, #challenge-stage, #challenge-running'
            )
            """;

    public Optional<CaptchaChallenge> detect(PageSnapshot snapshot, UUID jobId) {
        Page page = extractPlaywrightPage(snapshot);
        if (page == null) return Optional.empty();

        Optional<CaptchaChallenge> domHit = detectFromDom(page, snapshot, jobId);
        if (domHit.isPresent()) return domHit;

        if (isCloudflareChallenge(page)) {
            log.info("[CAPTCHA-DETECT] Cloudflare managed challenge (cross-origin iframe) url={} job={}",
                    snapshot.getUrl(), jobId);
            return Optional.of(CaptchaChallenge.builder()
                    .type(CaptchaType.CLOUDFLARE_INTERSTITIAL)
                    .siteKey("cf-managed")
                    .pageUrl(snapshot.getUrl())
                    .jobId(jobId)
                    .invisible(false)
                    .build());
        }

        return Optional.empty();
    }

    private Optional<CaptchaChallenge> detectFromDom(Page page, PageSnapshot snapshot, UUID jobId) {
        try {
            Object raw = page.evaluate(DETECTION_SCRIPT);
            if (raw == null) return Optional.empty();

            DetectionRaw parsed = parse(raw.toString());
            if (parsed == null) {
                log.warn("[CAPTCHA-DETECT] Malformed detection payload: {}", raw);
                return Optional.empty();
            }

            log.info("[CAPTCHA-DETECT] Found type={} sitekey={}... on url={} job={}",
                    parsed.type(),
                    parsed.siteKey().substring(0, Math.min(8, parsed.siteKey().length())),
                    snapshot.getUrl(), jobId);

            boolean isV3 = parsed.type() == CaptchaType.RECAPTCHA_V3;

            return Optional.of(CaptchaChallenge.builder()
                    .type(parsed.type())
                    .siteKey(parsed.siteKey())
                    .pageUrl(snapshot.getUrl())
                    .jobId(jobId)
                    .invisible(parsed.type() == CaptchaType.TURNSTILE)
                    .action(isV3 ? "submit" : null)
                    .minScore(isV3 ? 0.5 : null)
                    .build());

        } catch (Exception e) {
            log.warn("[CAPTCHA-DETECT] DOM probe failed for url={}: {}",
                    snapshot.getUrl(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isCloudflareChallenge(Page page) {
        try {
            if (Boolean.TRUE.equals(page.evaluate(CF_CHALLENGE_PROBE))) return true;

            return page.frames().stream().anyMatch(f -> {
                String u = f.url();
                return u != null && (u.contains("challenges.cloudflare.com")
                        || u.contains("/cdn-cgi/challenge-platform/"));
            });
        } catch (Exception e) {
            log.debug("[CAPTCHA-DETECT] Cloudflare probe failed: {}", e.getMessage());
            return false;
        }
    }

    private static Page extractPlaywrightPage(PageSnapshot snapshot) {
        if (snapshot == null) return null;
        LiveBrowserContext ctx = snapshot.getBrowserContext();
        return ctx != null && ctx.isPlaywright() ? ctx.getPage() : null;
    }

    private static DetectionRaw parse(String raw) {
        int separator = raw.indexOf('|');
        if (separator <= 0 || separator == raw.length() - 1) return null;

        String typeName = raw.substring(0, separator).toUpperCase(Locale.ROOT);
        String siteKey = raw.substring(separator + 1).trim();
        if (siteKey.isBlank()) return null;

        try {
            return new DetectionRaw(CaptchaType.valueOf(typeName), siteKey);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private record DetectionRaw(CaptchaType type, String siteKey) {
    }
}