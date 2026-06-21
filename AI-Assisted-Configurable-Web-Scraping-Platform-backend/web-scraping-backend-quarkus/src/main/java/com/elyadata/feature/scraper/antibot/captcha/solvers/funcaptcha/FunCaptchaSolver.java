package com.elyadata.feature.scraper.antibot.captcha.solvers.funcaptcha;

import com.elyadata.feature.scraper.antibot.captcha.solvers.support.ImageGridSolverSupport;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class FunCaptchaSolver {

    private static final int MAX_PUZZLES = 5;
    private static final int PRE_BEGIN_WAIT_MS = 2_000;
    private static final int PRE_BEGIN_JITTER_MS = 1_500;
    private static final int POST_BEGIN_WAIT_MS = 2_000;
    private static final int PUZZLE_RENDER_WAIT_MS = 1_800;
    private static final int POST_SUBMIT_WAIT_MS = 3_500;
    private static final int FRAME_PROBE_TIMEOUT_MS = 3_000;
    private static final int BEGIN_PROBE_TIMEOUT_MS = 2_000;

    private static final String[] OUTER_IFRAME_SELECTORS = {
            "iframe[src*='arkoselabs.com']",
            "iframe[src*='funcaptcha.com']",
            "iframe[id*='arkose']",
            "iframe[id*='FunCaptcha']",
            "iframe[data-e2e*='enforcement']",
            "iframe[title*='Arkose']"
    };

    private static final String[] INNER_IFRAME_SELECTORS = {
            "iframe[id*='game-core-frame']",
            "iframe[id*='fc-iframe-wrap']",
            "iframe[title*='Verification']",
            "iframe[title*='Vérification']",
            "iframe[src*='game-core']"
    };

    /** Probe element used to confirm we've actually reached the challenge body. */
    private static final String CHALLENGE_PROBE_SELECTOR =
            "#home_children_button, " +
                    "[data-theme*='challenge-btn'], " +
                    "button[aria-label*='egin'], " +
                    "button[aria-label*='ommencer'], " +
                    "[id*='game-core'], " +
                    "[class*='game-core'], " +
                    "[class*='challenge-container']";

    private static final String BEGIN_BUTTON_SELECTOR =
            "#home_children_button, " +
                    "button[data-theme*='challenge-btn'], " +
                    "button[aria-label*='egin'], " +
                    "button[aria-label*='Start'], " +
                    "button[aria-label*='ommencer'], " +
                    "button[aria-label*='Authent']";

    private final FunCaptchaRotateSolver rotateSolver;
    private final ImageGridSolverSupport support;

    public Optional<String> solve(Page page, UUID jobId) {
        try {
            log.info("[FUNCAPTCHA] Starting solve job={}", jobId);

            // Humans pause a moment before clicking widgets — Arkose scores this.
            page.waitForTimeout(PRE_BEGIN_WAIT_MS
                    + ThreadLocalRandom.current().nextInt(0, PRE_BEGIN_JITTER_MS));

            logFunCaptchaIframes(page, jobId);

            FrameLocator challenge = findChallengeFrame(page, jobId);
            if (challenge == null) {
                log.warn("[FUNCAPTCHA] Challenge frame not found across all selectors job={}", jobId);
                return Optional.empty();
            }

            clickBeginIfPresent(page, challenge, jobId);

            for (int puzzle = 1; puzzle <= MAX_PUZZLES; puzzle++) {
                log.info("[FUNCAPTCHA] Puzzle {}/{} job={}", puzzle, MAX_PUZZLES, jobId);

                page.waitForTimeout(PUZZLE_RENDER_WAIT_MS);

                // Token may have appeared during transition between puzzles.
                String token = readToken(page);
                if (token != null && !token.isBlank()) {
                    log.info("[FUNCAPTCHA] Solved after {} puzzle(s) job={}", puzzle - 1, jobId);
                    logTokenPreview(token);
                    return Optional.of(token);
                }

                boolean solved = rotateSolver.solveOnePuzzle(page, challenge, jobId);
                if (!solved) {
                    log.warn("[FUNCAPTCHA] Puzzle {} did not complete cleanly job={}", puzzle, jobId);
                    // Don't bail immediately — Arkose may still accept and roll to next.
                }

                page.waitForTimeout(POST_SUBMIT_WAIT_MS);

                token = readToken(page);
                if (token != null && !token.isBlank()) {
                    log.info("[FUNCAPTCHA] Token appeared after puzzle {} job={}", puzzle, jobId);
                    logTokenPreview(token);
                    return Optional.of(token);
                }
            }

            log.warn("[FUNCAPTCHA] Exhausted {} puzzles without token job={}", MAX_PUZZLES, jobId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[FUNCAPTCHA] Solve failed job={}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Walk the outer × inner iframe Cartesian product. FunCaptcha sometimes sits one
     * iframe deep, sometimes two. The first combination that exposes a known
     * challenge-body probe wins.
     */
    private FrameLocator findChallengeFrame(Page page, UUID jobId) {
        // Try nested (outer + inner) first — modern configurations.
        for (String outerSel : OUTER_IFRAME_SELECTORS) {
            for (String innerSel : INNER_IFRAME_SELECTORS) {
                try {
                    FrameLocator outer = page.frameLocator(outerSel);
                    FrameLocator inner = outer.frameLocator(innerSel);
                    Locator probe = inner.locator(CHALLENGE_PROBE_SELECTOR).first();
                    probe.waitFor(new Locator.WaitForOptions().setTimeout(FRAME_PROBE_TIMEOUT_MS));
                    log.info("[FUNCAPTCHA] Frame nested: outer='{}' inner='{}' job={}",
                            outerSel, innerSel, jobId);
                    return inner;
                } catch (Exception ignored) {
                }
            }
        }

        // Fallback: single-level iframe (older integrations or direct loader).
        for (String outerSel : OUTER_IFRAME_SELECTORS) {
            try {
                FrameLocator outer = page.frameLocator(outerSel);
                Locator probe = outer.locator(CHALLENGE_PROBE_SELECTOR).first();
                probe.waitFor(new Locator.WaitForOptions().setTimeout(FRAME_PROBE_TIMEOUT_MS));
                log.info("[FUNCAPTCHA] Frame single-level: '{}' job={}", outerSel, jobId);
                return outer;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void clickBeginIfPresent(Page page, FrameLocator challenge, UUID jobId) {
        try {
            Locator begin = challenge.locator(BEGIN_BUTTON_SELECTOR).first();
            if (begin.isVisible(new Locator.IsVisibleOptions().setTimeout(BEGIN_PROBE_TIMEOUT_MS))) {
                log.info("[FUNCAPTCHA] Begin/Authenticate button visible — clicking job={}", jobId);
                support.clickHumanlike(page, begin);
                page.waitForTimeout(POST_BEGIN_WAIT_MS);
            } else {
                log.debug("[FUNCAPTCHA] No begin button — already in puzzle phase job={}", jobId);
            }
        } catch (Exception e) {
            log.debug("[FUNCAPTCHA] Begin button probe failed (likely absent): {}", e.getMessage());
        }
    }

    /**
     * Read the verification token from the host page. FunCaptcha writes the token
     * into a hidden input on the parent document (not inside the iframe).
     */
    private String readToken(Page page) {
        try {
            Object val = page.evaluate("""
                    () => {
                      const candidates = [
                        'input[name="fc-token"]',
                        'input[id="fc-token"]',
                        'input[name="verification_token"]',
                        'input[name="arkose-token"]',
                        'input[name="arkoseToken"]',
                        'input[name="arkose_token"]'
                      ];
                      for (const sel of candidates) {
                        const el = document.querySelector(sel);
                        if (el && el.value && el.value.length > 10) return el.value;
                      }
                      // Some integrations store it on a global handle.
                      try {
                        if (window.fc && window.fc.token) return window.fc.token;
                        if (window.ArkoseEnforcement && window.ArkoseEnforcement.token) {
                          return window.ArkoseEnforcement.token;
                        }
                      } catch (e) {}
                      // Fallback: scan every input for a token-shaped value.
                      const all = document.querySelectorAll('input');
                      for (const el of all) {
                        const v = el.value || '';
                        if (v.length > 30 && /\\|r=|\\|sup=|\\|rid=/.test(v)) return v;
                      }
                      return '';
                    }
                    """);
            return val == null ? null : val.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void logFunCaptchaIframes(Page page, UUID jobId) {
        try {
            Object iframes = page.evaluate("""
                    () => Array.from(document.querySelectorAll('iframe'))
                      .filter(f => {
                        const s = (f.src || '').toLowerCase();
                        const t = (f.title || '').toLowerCase();
                        const i = (f.id || '').toLowerCase();
                        return s.includes('arkose') || s.includes('funcaptcha')
                            || t.includes('arkose') || t.includes('verification')
                            || i.includes('arkose') || i.includes('funcaptcha');
                      })
                      .map(f => ({
                        src: f.src ? f.src.substring(0, 100) : '(no-src)',
                        title: f.title || '(no-title)',
                        id: f.id || '(no-id)',
                        name: f.name || '(no-name)'
                      }))
                    """);
            log.info("[FUNCAPTCHA-DIAG] arkose iframes={} job={}", iframes, jobId);
        } catch (Exception ignored) {
        }
    }

    private void logTokenPreview(String token) {
        log.info("[FUNCAPTCHA] Token preview: {}... (len={})",
                token.substring(0, Math.min(40, token.length())), token.length());
    }
}