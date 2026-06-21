package com.elyadata.feature.scraper.antibot.captcha.orchestration;

import com.elyadata.feature.scraper.antibot.captcha.cache.CaptchaTokenPort;
import com.elyadata.feature.scraper.antibot.captcha.core.*;
import com.elyadata.feature.scraper.antibot.captcha.detection.ImageCaptchaDetector;
import com.elyadata.feature.scraper.antibot.captcha.detection.LiveCaptchaDetector;
import com.elyadata.feature.scraper.antibot.captcha.detection.LlmFallbackCaptchaDetector;
import com.elyadata.feature.scraper.antibot.captcha.intervention.service.HumanInterventionService;
import com.elyadata.feature.scraper.antibot.captcha.metrics.CaptchaMetrics;
import com.elyadata.feature.scraper.antibot.captcha.solvers.funcaptcha.FunCaptchaSolver;
import com.elyadata.feature.scraper.antibot.captcha.solvers.geetest.GeetestSliderSolver;
import com.elyadata.feature.scraper.antibot.captcha.solvers.hcaptcha.HcaptchaImageSolver;
import com.elyadata.feature.scraper.antibot.captcha.solvers.llm.LlmPilotSolver;
import com.elyadata.feature.scraper.antibot.captcha.solvers.recaptcha.RecaptchaV2ImageSolver;
import com.elyadata.feature.scraper.antibot.captcha.solvers.recaptcha.RecaptchaV3NativeSolver;
import com.elyadata.feature.scraper.antibot.captcha.solvers.rotate.RotateCaptchaSolver;
import com.elyadata.feature.scraper.antibot.captcha.solvers.turnstile.TurnstileSolver;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CaptchaHandler {

    private static final long POST_INJECT_WAIT_MS = 1_500;

    private static final String VERIFY_PROBE_JS = """
            () => {
              const body = (document.body && document.body.innerText || '');
              const stillCf = !!document.querySelector(
                'iframe[src*="challenges.cloudflare.com"], [id^="cf-chl-widget-"], ' +
                '.cf-turnstile[data-sitekey], #challenge-stage, #challenge-form, #challenge-running'
              );
              return {
                title: document.title || '',
                bodyLen: body.length,
                bodySample: body.substring(0, 200),
                stillCf: stillCf
              };
            }
            """;

    private static final Map<CaptchaType, Integer> CACHE_TTL_OVERRIDES = Map.of(
            CaptchaType.TURNSTILE, 0,
            CaptchaType.CLOUDFLARE_INTERSTITIAL, 0,
            CaptchaType.IMAGE_TEXT, 0,
            CaptchaType.GEETEST, 0,
            CaptchaType.RECAPTCHA_V3, 0,
            CaptchaType.RECAPTCHA_V2, 2,
            CaptchaType.FUNCAPTCHA, 0
    );

    private static final Set<CaptchaType> IFRAME_SANDBOXED_TYPES = Set.of(
            CaptchaType.RECAPTCHA_V2,
            CaptchaType.RECAPTCHA_V3,
            CaptchaType.HCAPTCHA,
            CaptchaType.TURNSTILE,
            CaptchaType.CLOUDFLARE_INTERSTITIAL,
            CaptchaType.FUNCAPTCHA
    );

    private static final Set<CaptchaType> LLM_PILOT_ONLY_TYPES = Set.of(
            CaptchaType.CUSTOM,
            CaptchaType.UNKNOWN
    );

    private static final String[] POST_CAPTCHA_SUBMIT_SELECTORS = {
            "button[type='submit']:visible",
            "input[type='submit']:visible",
            "button:has-text('Vérifier')",
            "button:has-text('Verify')",
            "button:has-text('Check')",
            "button:has-text('Submit')",
            "button:has-text('Envoyer')",
            "button.btn-primary:visible",
            "form button:visible"
    };

    private final LiveCaptchaDetector detector;
    private final ImageCaptchaDetector imageDetector;
    private final LlmFallbackCaptchaDetector llmFallbackDetector;
    private final CaptchaSolverRegistry solverRegistry;
    private final CaptchaTokenInjector tokenInjector;
    private final CaptchaTokenPort tokenCache;
    private final RecaptchaV2ImageSolver recaptchaV2ImageSolver;
    private final HcaptchaImageSolver hcaptchaImageSolver;
    private final GeetestSliderSolver geetestSliderSolver;
    private final TurnstileSolver turnstileSolver;
    private final FunCaptchaSolver funCaptchaSolver;
    private final RotateCaptchaSolver rotateCaptchaSolver;
    private final CaptchaMetrics metrics;
    private final LlmPilotSolver llmPilotSolver;
    private final RecaptchaV3NativeSolver recaptchaV3NativeSolver;
    private final HumanInterventionService humanInterventionService;

    @ConfigProperty(name = "captcha.cache.ttl-minutes", defaultValue = "5")
    int cacheTtlMinutes;

    @ConfigProperty(name = "captcha.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "captcha.prefer-llm-vision", defaultValue = "true")
    boolean preferLlmVision;

    @ConfigProperty(name = "captcha.force-human", defaultValue = "false")
    boolean forceHuman;

    public boolean tryHandleIfPresent(PageSnapshot snapshot, UUID jobId) {
        if (!enabled) return false;

        Optional<CaptchaChallenge> tokenChallenge = detector.detect(snapshot, jobId);
        if (tokenChallenge.isPresent()) {
            return handleChallenge(snapshot, tokenChallenge.get());
        }

        Optional<CaptchaChallenge> imageChallenge = imageDetector.detect(snapshot, jobId);
        if (imageChallenge.isPresent()) {
            return handleChallenge(snapshot, imageChallenge.get());
        }

        Optional<CaptchaChallenge> llmChallenge = llmFallbackDetector.detect(snapshot, jobId);
        return llmChallenge.filter(c -> handleChallenge(snapshot, c)).isPresent();
    }

    private boolean handleChallenge(PageSnapshot snapshot, CaptchaChallenge challenge) {
        String domain = extractDomain(challenge.pageUrl());

        if (challenge.type().isTokenBased()
                && challenge.type() != CaptchaType.TURNSTILE
                && challenge.type() != CaptchaType.CLOUDFLARE_INTERSTITIAL
                && tryReuseCachedToken(snapshot, challenge, domain)) {
            return true;
        }

        return solveAndApply(snapshot, challenge, domain);
    }

    private boolean tryReuseCachedToken(PageSnapshot snapshot,
                                        CaptchaChallenge challenge,
                                        String domain) {
        Optional<CaptchaTokenPort.CachedToken> cached = tokenCache.find(domain, challenge.type());
        if (cached.isEmpty()) return false;

        log.info("[CAPTCHA-HANDLER] Reusing cached token domain={} type={} job={}",
                domain, challenge.type(), challenge.jobId());

        boolean injected = tokenInjector.inject(snapshot, challenge.type(), cached.get().token());
        if (!injected) {
            tokenCache.invalidate(domain, challenge.type());
            log.warn("[CAPTCHA-HANDLER] Cached token injection failed — invalidated for {}", domain);
            return false;
        }
        waitAfterInjection();
        return true;
    }

    private boolean solveAndApply(PageSnapshot snapshot,
                                  CaptchaChallenge challenge,
                                  String domain) {
        long startMs = System.currentTimeMillis();

        if (forceHuman) {
            log.warn("[CAPTCHA-HANDLER] captcha.force-human=true — bypassing all solvers, " +
                    "escalating directly to human for type={} domain={}", challenge.type(), domain);
            return escalateToHuman(snapshot, challenge, domain);
        }

        if (IFRAME_SANDBOXED_TYPES.contains(challenge.type())
                && !hasCodedSolver(challenge.type())) {
            log.warn("[CAPTCHA-HANDLER] Type={} is iframe-sandboxed with no coded solver — " +
                            "skipping LLM-pilot. Trying human intervention for domain={}",
                    challenge.type(), domain);
            return escalateToHuman(snapshot, challenge, domain);
        }

        boolean solverCodedSucceeded = runCodedSolver(snapshot, challenge, domain, startMs);
        if (solverCodedSucceeded) return true;

        if (IFRAME_SANDBOXED_TYPES.contains(challenge.type())) {
            log.warn("[CAPTCHA-HANDLER] Coded solver failed for iframe-sandboxed type={} — " +
                            "LLM-pilot cannot help. Trying human intervention for domain={}",
                    challenge.type(), domain);
            return escalateToHuman(snapshot, challenge, domain);
        }

        log.info("[CAPTCHA-HANDLER] Escalating to LLM-pilot for type={} domain={}",
                challenge.type(), domain);
        boolean pilotSucceeded = runLlmPilot(snapshot, challenge, domain);
        if (pilotSucceeded) return true;

        return escalateToHuman(snapshot, challenge, domain);
    }

    private boolean escalateToHuman(PageSnapshot snapshot,
                                    CaptchaChallenge challenge,
                                    String domain) {
        if (!humanInterventionService.supports(challenge.type())) {
            log.warn("[CAPTCHA-HANDLER] Human intervention not supported for type={} — giving up domain={}",
                    challenge.type(), domain);
            return false;
        }

        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-HANDLER] No Playwright page — cannot escalate to human");
            return false;
        }

        long startMs = System.currentTimeMillis();
        metrics.recordAttempt(challenge.type(), CaptchaProvider.HUMAN);

        boolean solved = humanInterventionService.requestInterventionAndWait(snapshot, challenge, page);
        long elapsed = System.currentTimeMillis() - startMs;
        metrics.recordLatency(challenge.type(), CaptchaProvider.HUMAN, elapsed);

        if (solved) {
            metrics.recordSuccess(challenge.type(), CaptchaProvider.HUMAN);
            log.info("[CAPTCHA-HANDLER] Bypassed type={} domain={} via HUMAN in {}ms",
                    challenge.type(), domain, elapsed);
            waitAfterInjection();
            return true;
        }

        metrics.recordFailure(challenge.type(), CaptchaProvider.HUMAN, "human_failed_or_timeout");
        log.warn("[CAPTCHA-HANDLER] Human intervention failed/timed out for domain={}", domain);
        return false;
    }

    private static boolean hasCodedSolver(CaptchaType type) {
        return switch (type) {
            case RECAPTCHA_V2, RECAPTCHA_V3, HCAPTCHA, TURNSTILE,
                 CLOUDFLARE_INTERSTITIAL,
                 GEETEST, IMAGE_TEXT, FUNCAPTCHA, ROTATE -> true;
            case CUSTOM, UNKNOWN -> false;
        };
    }

    private boolean runCodedSolver(PageSnapshot snapshot,
                                   CaptchaChallenge challenge,
                                   String domain,
                                   long startMs) {
        if (LLM_PILOT_ONLY_TYPES.contains(challenge.type())) {
            log.info("[CAPTCHA-HANDLER] No coded solver for type={} — routing to LLM-pilot",
                    challenge.type());
            return false;
        }

        if (preferLlmVision && challenge.type() == CaptchaType.RECAPTCHA_V2) {
            return runInteractiveSolver(snapshot, challenge, domain, startMs,
                    page -> recaptchaV2ImageSolver.solve(page, challenge.jobId()));
        }
        if (preferLlmVision && challenge.type() == CaptchaType.HCAPTCHA) {
            return runInteractiveSolver(snapshot, challenge, domain, startMs,
                    page -> hcaptchaImageSolver.solve(page, challenge.jobId()));
        }
        if (preferLlmVision && challenge.type() == CaptchaType.GEETEST) {
            return runGeetestSolver(snapshot, challenge, domain, startMs);
        }
        if (preferLlmVision && challenge.type() == CaptchaType.TURNSTILE) {
            return runInteractiveSolver(snapshot, challenge, domain, startMs,
                    page -> turnstileSolver.solve(page, challenge.jobId()));
        }
        if (challenge.type() == CaptchaType.CLOUDFLARE_INTERSTITIAL) {
            return runCloudflareInterstitialSolver(snapshot, challenge, domain, startMs);
        }
        if (challenge.type() == CaptchaType.RECAPTCHA_V3) {
            return runV3NativeSolver(snapshot, challenge, domain, startMs);
        }
        if (challenge.type() == CaptchaType.FUNCAPTCHA) {
            return runInteractiveSolver(snapshot, challenge, domain, startMs,
                    page -> funCaptchaSolver.solve(page, challenge.jobId()));
        }
        if (challenge.type() == CaptchaType.ROTATE) {
            return runRotateSolver(snapshot, challenge, domain, startMs);
        }

        Optional<CaptchaSolver> solverOpt = solverRegistry.resolve(challenge.type());
        if (solverOpt.isEmpty()) {
            log.info("[CAPTCHA-HANDLER] No coded solver for type={} — will try LLM-pilot",
                    challenge.type());
            return false;
        }

        CaptchaSolver solver = solverOpt.get();
        metrics.recordAttempt(challenge.type(), solver.provider());

        CaptchaSolution solution;
        try {
            solution = solver.solve(challenge);
        } catch (CaptchaSolverException e) {
            log.warn("[CAPTCHA-HANDLER] Solver {} threw: {}", solver.provider(), e.getMessage());
            metrics.recordFailure(challenge.type(), solver.provider(), "exception");
            return false;
        }

        long elapsed = System.currentTimeMillis() - startMs;
        metrics.recordLatency(challenge.type(), solver.provider(), elapsed);

        if (!solution.success() || solution.token() == null) {
            log.warn("[CAPTCHA-HANDLER] Solver {} failed: {}",
                    solution.provider(), solution.errorMessage());
            metrics.recordFailure(challenge.type(), solver.provider(), "no_solution");
            return false;
        }

        boolean applied = applySolution(snapshot, challenge, solution.token(), domain);
        if (applied) {
            metrics.recordSuccess(challenge.type(), solver.provider());
            log.info("[CAPTCHA-HANDLER] Bypassed type={} domain={} via {} in {}ms",
                    challenge.type(), domain, solution.provider(), elapsed);
        } else {
            metrics.recordFailure(challenge.type(), solver.provider(), "apply_failed");
        }
        return applied;
    }

    private boolean runLlmPilot(PageSnapshot snapshot,
                                CaptchaChallenge challenge,
                                String domain) {
        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-HANDLER] No Playwright page — cannot run LLM-pilot for {}",
                    challenge.type());
            return false;
        }

        long pilotStartMs = System.currentTimeMillis();
        metrics.recordAttempt(challenge.type(), CaptchaProvider.LLM_VISION);

        boolean solved = llmPilotSolver.solve(page, challenge.jobId());
        long elapsed = System.currentTimeMillis() - pilotStartMs;
        metrics.recordLatency(challenge.type(), CaptchaProvider.LLM_VISION, elapsed);

        if (solved) {
            metrics.recordSuccess(challenge.type(), CaptchaProvider.LLM_VISION);
            log.info("[CAPTCHA-HANDLER] LLM-pilot bypass type={} domain={} in {}ms",
                    challenge.type(), domain, elapsed);
            waitAfterInjection();
            return true;
        }

        metrics.recordFailure(challenge.type(), CaptchaProvider.LLM_VISION, "llm_pilot_failed");
        log.warn("[CAPTCHA-HANDLER] LLM-pilot failed for domain={}", domain);
        return false;
    }

    private boolean runInteractiveSolver(PageSnapshot snapshot,
                                         CaptchaChallenge challenge,
                                         String domain,
                                         long startMs,
                                         InteractiveSolverFn fn) {
        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-HANDLER] No Playwright page — cannot run interactive solver for {}",
                    challenge.type());
            return false;
        }
        metrics.recordAttempt(challenge.type(), CaptchaProvider.LLM_VISION);

        Optional<String> token = fn.apply(page);
        long elapsed = System.currentTimeMillis() - startMs;
        metrics.recordLatency(challenge.type(), CaptchaProvider.LLM_VISION, elapsed);

        if (token.isEmpty() || token.get().isBlank()) {
            metrics.recordFailure(challenge.type(), CaptchaProvider.LLM_VISION, "solver_returned_empty");
            log.warn("[CAPTCHA-HANDLER] Interactive solver returned no token for type={} domain={}",
                    challenge.type(), domain);
            return false;
        }

        cacheToken(domain, challenge.type(), token.get());
        metrics.recordSuccess(challenge.type(), CaptchaProvider.LLM_VISION);
        log.info("[CAPTCHA-HANDLER] Interactive bypass type={} domain={} in {}ms",
                challenge.type(), domain, elapsed);
        waitAfterInjection();
        return true;
    }

    private boolean runGeetestSolver(PageSnapshot snapshot,
                                     CaptchaChallenge challenge,
                                     String domain,
                                     long startMs) {
        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-HANDLER] No Playwright page — cannot run GeeTest solver");
            return false;
        }

        metrics.recordAttempt(challenge.type(), CaptchaProvider.LLM_VISION);

        boolean solved = geetestSliderSolver.solve(page, challenge.jobId());
        long elapsed = System.currentTimeMillis() - startMs;
        metrics.recordLatency(challenge.type(), CaptchaProvider.LLM_VISION, elapsed);

        if (solved) {
            metrics.recordSuccess(challenge.type(), CaptchaProvider.LLM_VISION);
            log.info("[CAPTCHA-HANDLER] GeeTest bypassed domain={} in {}ms", domain, elapsed);
            waitAfterInjection();
            return true;
        }

        metrics.recordFailure(challenge.type(), CaptchaProvider.LLM_VISION, "geetest_failed");
        log.warn("[CAPTCHA-HANDLER] GeeTest solver failed for domain={}", domain);
        return false;
    }

    private boolean applySolution(PageSnapshot snapshot,
                                  CaptchaChallenge challenge,
                                  String solutionValue,
                                  String domain) {
        boolean applied;
        if (challenge.type() == CaptchaType.IMAGE_TEXT) {
            applied = tokenInjector.injectTextAnswer(
                    snapshot, challenge.inputSelector(), solutionValue);
        } else {
            applied = tokenInjector.inject(snapshot, challenge.type(), solutionValue);
            if (applied) cacheToken(domain, challenge.type(), solutionValue);
        }

        if (!applied) {
            log.warn("[CAPTCHA-HANDLER] Apply failed for type={} domain={}",
                    challenge.type(), domain);
            return false;
        }
        waitAfterInjection();
        return true;
    }

    private void cacheToken(String domain, CaptchaType type, String token) {
        int ttl = CACHE_TTL_OVERRIDES.getOrDefault(type, cacheTtlMinutes);
        if (ttl <= 0) return;
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(ttl));
        tokenCache.put(domain, type, token, expiresAt);
    }

    private static Page extractPage(PageSnapshot snapshot) {
        if (snapshot == null) return null;
        LiveBrowserContext ctx = snapshot.getBrowserContext();
        return ctx != null && ctx.isPlaywright() ? ctx.getPage() : null;
    }

    private static void waitAfterInjection() {
        try {
            Thread.sleep(POST_INJECT_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extractDomain(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host.toLowerCase().replaceFirst("^www\\.", "") : url;
        } catch (Exception e) {
            return url;
        }
    }

    private boolean runV3NativeSolver(PageSnapshot snapshot,
                                      CaptchaChallenge challenge,
                                      String domain,
                                      long startMs) {
        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-HANDLER] No Playwright page — cannot solve v3 domain={}", domain);
            return false;
        }

        metrics.recordAttempt(challenge.type(), CaptchaProvider.LLM_VISION);

        Optional<String> token = recaptchaV3NativeSolver.solve(
                page, challenge.siteKey(), challenge.action(), challenge.jobId());

        long elapsed = System.currentTimeMillis() - startMs;
        metrics.recordLatency(challenge.type(), CaptchaProvider.LLM_VISION, elapsed);

        if (token.isEmpty()) {
            metrics.recordFailure(challenge.type(), CaptchaProvider.LLM_VISION, "native_failed");
            log.warn("[CAPTCHA-HANDLER] v3 native failed for domain={} in {}ms",
                    domain, elapsed);
            return false;
        }

        boolean applied = applySolution(snapshot, challenge, token.get(), domain);
        if (applied) {
            metrics.recordSuccess(challenge.type(), CaptchaProvider.LLM_VISION);
            log.info("[CAPTCHA-HANDLER] v3 bypassed natively domain={} in {}ms",
                    domain, elapsed);
            clickPostCaptchaSubmit(page, challenge.jobId());
        } else {
            metrics.recordFailure(challenge.type(), CaptchaProvider.LLM_VISION, "apply_failed");
        }
        return applied;
    }

    private void clickPostCaptchaSubmit(Page page, UUID jobId) {
        for (String selector : POST_CAPTCHA_SUBMIT_SELECTORS) {
            try {
                Locator btn = page.locator(selector).first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(800))) {
                    log.info("[CAPTCHA-HANDLER] Clicking post-captcha submit via '{}' job={}",
                            selector, jobId);
                    btn.scrollIntoViewIfNeeded();
                    page.waitForTimeout(300);
                    btn.click();
                    page.waitForTimeout(2_500);
                    return;
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        log.info("[CAPTCHA-HANDLER] No post-captcha submit button found job={}", jobId);
    }

    private boolean runRotateSolver(PageSnapshot snapshot,
                                    CaptchaChallenge challenge,
                                    String domain,
                                    long startMs) {
        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-HANDLER] No Playwright page — cannot run rotate solver");
            return false;
        }

        metrics.recordAttempt(challenge.type(), CaptchaProvider.LLM_VISION);

        boolean solved = rotateCaptchaSolver.solve(page, challenge.jobId());
        long elapsed = System.currentTimeMillis() - startMs;
        metrics.recordLatency(challenge.type(), CaptchaProvider.LLM_VISION, elapsed);

        if (solved) {
            metrics.recordSuccess(challenge.type(), CaptchaProvider.LLM_VISION);
            log.info("[CAPTCHA-HANDLER] Rotate captcha bypassed domain={} in {}ms", domain, elapsed);
            waitAfterInjection();
            return true;
        }

        metrics.recordFailure(challenge.type(), CaptchaProvider.LLM_VISION, "rotate_failed");
        log.warn("[CAPTCHA-HANDLER] Rotate solver failed for domain={}", domain);
        return false;
    }

    private boolean runCloudflareInterstitialSolver(PageSnapshot snapshot,
                                                    CaptchaChallenge challenge,
                                                    String domain,
                                                    long startMs) {
        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-HANDLER] No Playwright page — cannot run Cloudflare interstitial solver");
            return false;
        }

        metrics.recordAttempt(challenge.type(), CaptchaProvider.LLM_VISION);

        String beforeUrl = safeUrl(page);
        Optional<String> token = turnstileSolver.solve(page, challenge.jobId());
        long elapsed = System.currentTimeMillis() - startMs;
        metrics.recordLatency(challenge.type(), CaptchaProvider.LLM_VISION, elapsed);

        boolean tokenOk = token.isPresent() && !token.get().isBlank();
        boolean clearance = hasCfClearance(page);
        boolean leftChallenge = leftChallengePage(page, beforeUrl);

        logCfVerification(page, domain, challenge.jobId());

        if (tokenOk || clearance || leftChallenge) {
            metrics.recordSuccess(challenge.type(), CaptchaProvider.LLM_VISION);
            log.info("[CAPTCHA-HANDLER] Cloudflare interstitial cleared domain={} " +
                            "(token={}, clearance={}, redirected={}) in {}ms",
                    domain, tokenOk, clearance, leftChallenge, elapsed);
            waitAfterInjection();
            return true;
        }


        metrics.recordFailure(challenge.type(), CaptchaProvider.LLM_VISION, "interstitial_not_cleared");
        log.warn("[CAPTCHA-HANDLER] Cloudflare interstitial not cleared for domain={} in {}ms " +
                "(token=false, clearance=false, redirected=false)", domain, elapsed);
        return false;
    }

    private void logCfVerification(Page page, String domain, UUID jobId) {
        try {
            Object probe = page.evaluate(VERIFY_PROBE_JS);
            if (probe instanceof Map<?, ?> m) {
                log.info("[CF-VERIFY] domain={} job={} finalUrl={} title='{}' bodyLen={} stillCf={} cf_clearance={} sample='{}'",
                        domain, jobId, safeUrl(page),
                        m.get("title"), m.get("bodyLen"), m.get("stillCf"),
                        hasCfClearance(page),
                        String.valueOf(m.get("bodySample")).replaceAll("\\s+", " "));
            }
        } catch (Exception e) {
            log.warn("[CF-VERIFY] probe failed domain={} job={}: {}", domain, jobId, e.getMessage());
        }
    }

    private static boolean hasCfClearance(Page page) {
        try {
            return page.context().cookies().stream()
                    .anyMatch(c -> "cf_clearance".equals(c.name)
                            && c.value != null && !c.value.isBlank());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean leftChallengePage(Page page, String beforeUrl) {
        String now = safeUrl(page);
        if (now == null) return false;
        boolean stillOnChallenge = now.contains("/cdn-cgi/challenge-platform/")
                || now.contains("__cf_chl_")
                || now.contains("__cf_chl_tk");
        if (stillOnChallenge) return false;
        return !now.equals(beforeUrl);
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface InteractiveSolverFn {
        Optional<String> apply(Page page);
    }
}