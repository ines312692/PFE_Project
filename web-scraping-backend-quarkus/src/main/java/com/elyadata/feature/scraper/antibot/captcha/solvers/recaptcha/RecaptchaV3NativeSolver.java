package com.elyadata.feature.scraper.antibot.captcha.solvers.recaptcha;

import com.elyadata.feature.scraper.browser.humanizer.motion.BezierMouseMotion;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RecaptchaV3NativeSolver {

    private static final int READY_TIMEOUT_MS = 10_000;
    private static final int EXECUTE_TIMEOUT_MS = 15_000;
    private static final int MIN_TOKEN_LENGTH = 100;
    private static final int WARMUP_MOUSE_PASSES = 4;

    private final BezierMouseMotion mouseMotion;

    @ConfigProperty(name = "captcha.v3.warmup-base-ms", defaultValue = "3000")
    int warmupBaseMs;

    @ConfigProperty(name = "captcha.v3.warmup-jitter-ms", defaultValue = "2000")
    int warmupJitterMs;

    public Optional<String> solve(Page page, String siteKey, String action, UUID jobId) {
        try {
            log.info("[V3-NATIVE] start siteKey={} action={} job={}",
                    mask(siteKey), action, jobId);
            warmUp(page, jobId);
            page.waitForFunction(
                    "() => window.grecaptcha && typeof window.grecaptcha.ready === 'function'",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(READY_TIMEOUT_MS));
            String executeJs = """
                    async ({siteKey, action, timeoutMs}) => {
                      return new Promise((resolve, reject) => {
                        const timer = setTimeout(() => reject(new Error('execute_timeout')), timeoutMs);
                        try {
                          window.grecaptcha.ready(() => {
                            window.grecaptcha.execute(siteKey, { action })
                              .then(token => { clearTimeout(timer); resolve(token); })
                              .catch(err => { clearTimeout(timer); reject(err); });
                          });
                        } catch (e) {
                          clearTimeout(timer);
                          reject(e);
                        }
                      });
                    }
                    """;

            Object result = page.evaluate(executeJs, Map.of(
                    "siteKey", siteKey,
                    "action", action,
                    "timeoutMs", EXECUTE_TIMEOUT_MS));

            String token = result == null ? "" : result.toString().trim();

            if (token.length() < MIN_TOKEN_LENGTH) {
                log.warn("[V3-NATIVE] token too short ({}c) — likely failed job={}",
                        token.length(), jobId);
                return Optional.empty();
            }

            log.info("[V3-NATIVE] token acquired len={} preview={}... job={}",
                    token.length(),
                    token.substring(0, Math.min(20, token.length())),
                    jobId);
            return Optional.of(token);

        } catch (Exception e) {
            log.warn("[V3-NATIVE] solve failed job={}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    private void warmUp(Page page, UUID jobId) {
        try {
            ViewportSize vp = page.viewportSize();
            int width = vp != null ? vp.width : 1280;
            int height = vp != null ? vp.height : 720;
            for (int i = 0; i < WARMUP_MOUSE_PASSES; i++) {
                double tx = ThreadLocalRandom.current().nextDouble(100, width - (double)100);
                double ty = ThreadLocalRandom.current().nextDouble(100, height - (double)100);
                mouseMotion.moveTo(page, tx, ty, 24);
                sleepMs((long) ((double)200 + ThreadLocalRandom.current().nextInt(0, 400)));
            }
            mouseMotion.idleMicroMovements(page, 2);
            page.mouse().wheel(0, 200 + (double)ThreadLocalRandom.current().nextInt(0, 250));
            sleepMs((long) (500 + (double)ThreadLocalRandom.current().nextInt(0, 400)));
            page.mouse().wheel(0, -100 + (double)ThreadLocalRandom.current().nextInt(0, 80));
            sleepMs(400);
            sleepMs((long) ((double)warmupBaseMs + ThreadLocalRandom.current().nextInt(0, warmupJitterMs)));

            log.debug("[V3-NATIVE] warmUp done job={}", jobId);
        } catch (Exception e) {
            log.debug("[V3-NATIVE] warmUp non-fatal error: {}", e.getMessage());
        }
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String mask(String s) {
        return s == null || s.length() < 10 ? "?" : s.substring(0, 6) + "...";
    }
}