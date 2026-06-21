package com.elyadata.feature.scraper.antibot.captcha.solvers.funcaptcha;

import com.elyadata.common.llm.LlmRateLimitException;
import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.solvers.support.ImageGridSolverSupport;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class FunCaptchaRotateSolver {

    private static final int VOTING_PASSES = 3;
    private static final int STEP_DEGREES = 51;
    private static final int MAX_CLICKS_GUARD = 7;
    private static final int CLICK_DELAY_MIN_MS = 280;
    private static final int CLICK_DELAY_JITTER_MS = 220;
    private static final int POST_ROTATE_WAIT_MS = 600;
    private static final int PUZZLE_PROBE_TIMEOUT_MS = 5_000;
    private static final String PUZZLE_IMAGE_SELECTOR =
            "img[data-testid*='challenge'], " +
                    "[class*='puzzle-image'] img, " +
                    "[class*='answer'] img, " +
                    "#image_pool img, " +
                    "canvas[id*='puzzle'], " +
                    "[id*='puzzle-image'] img, " +
                    "div[role='img']";

    private static final String LEFT_ARROW_SELECTOR =
            "a[aria-label*='Previous'], " +
                    "a[aria-label*='récédent'], " +
                    "button[aria-label*='left'], " +
                    "button[aria-label*='auche'], " +
                    "button[data-theme*='rotate-left'], " +
                    "#left-arrow, " +
                    "[data-testid='left-button']";

    private static final String RIGHT_ARROW_SELECTOR =
            "a[aria-label*='Next'], " +
                    "a[aria-label*='uivant'], " +
                    "button[aria-label*='right'], " +
                    "button[aria-label*='roite'], " +
                    "button[data-theme*='rotate-right'], " +
                    "#right-arrow, " +
                    "[data-testid='right-button']";

    private static final String SUBMIT_SELECTOR =
            "button[type='submit'], " +
                    "button[aria-label*='Submit'], " +
                    "button[aria-label*='oumettre'], " +
                    "button[aria-label*='Verify'], " +
                    "button[aria-label*='érifier'], " +
                    "button[data-theme*='submit'], " +
                    "#verify-button, " +
                    "#verify-button-text";

    private final ImageGridSolverSupport support;
    private final VisionLlmClient visionClient;

    public boolean solveOnePuzzle(Page page, FrameLocator challenge, UUID jobId) {
        byte[] image;
        try {
            Locator puzzle = challenge.locator(PUZZLE_IMAGE_SELECTOR).first();
            puzzle.waitFor(new Locator.WaitForOptions().setTimeout(PUZZLE_PROBE_TIMEOUT_MS));
            image = puzzle.screenshot();
        } catch (Exception e) {
            log.warn("[FUNCAPTCHA-ROTATE] Failed to screenshot puzzle image job={}: {}",
                    jobId, e.getMessage());
            return false;
        }
        if (image == null || image.length == 0) {
            log.warn("[FUNCAPTCHA-ROTATE] Empty puzzle image bytes job={}", jobId);
            return false;
        }

        log.info("[FUNCAPTCHA-ROTATE] Puzzle screenshot captured ({} bytes) job={}",
                image.length, jobId);

        String b64 = Base64.getEncoder().encodeToString(image);
        Integer rotation = askLlmForRotationWithVoting(b64, jobId);
        if (rotation == null) {
            log.warn("[FUNCAPTCHA-ROTATE] No consensus on rotation — submitting as-is job={}", jobId);
            clickSubmit(page, challenge);
            return true;
        }
        boolean useRightArrow = rotation < 0;
        int absDeg = Math.abs(rotation);
        int forwardClicks = (int) Math.round(absDeg / (double) STEP_DEGREES);
        int reverseClicks = (int) Math.round((360 - absDeg) / (double) STEP_DEGREES);
        int clicks;
        if (reverseClicks < forwardClicks) {
            clicks = reverseClicks;
            useRightArrow = !useRightArrow;
            log.debug("[FUNCAPTCHA-ROTATE] Reverse path shorter ({} vs {})", reverseClicks, forwardClicks);
        } else {
            clicks = forwardClicks;
        }
        if (clicks > MAX_CLICKS_GUARD) clicks = MAX_CLICKS_GUARD;

        log.info("[FUNCAPTCHA-ROTATE] rotation={}° → {} arrow click(s) {} job={}",
                rotation, clicks, useRightArrow ? "RIGHT" : "LEFT", jobId);

        // ---- 4. Click arrows ----
        String arrowSel = useRightArrow ? RIGHT_ARROW_SELECTOR : LEFT_ARROW_SELECTOR;
        for (int i = 0; i < clicks; i++) {
            try {
                Locator arrow = challenge.locator(arrowSel).first();
                support.clickHumanlike(page, arrow);
                int delay = CLICK_DELAY_MIN_MS
                        + ThreadLocalRandom.current().nextInt(0, CLICK_DELAY_JITTER_MS);
                page.waitForTimeout(delay);
            } catch (Exception e) {
                log.warn("[FUNCAPTCHA-ROTATE] Arrow click {} failed: {}", i + 1, e.getMessage());
                // Continue — partial rotation may still be useful or recoverable.
            }
        }

        page.waitForTimeout(POST_ROTATE_WAIT_MS);
        clickSubmit(page, challenge);
        return true;
    }

    private void clickSubmit(Page page, FrameLocator challenge) {
        try {
            Locator submit = challenge.locator(SUBMIT_SELECTOR).first();
            support.clickHumanlike(page, submit);
        } catch (Exception e) {
            log.warn("[FUNCAPTCHA-ROTATE] Submit button click failed: {}", e.getMessage());
        }
    }

    private Integer askLlmForRotationWithVoting(String b64, UUID jobId) {
        List<Integer> rotations = new ArrayList<>();
        for (int i = 0; i < VOTING_PASSES; i++) {
            Integer r = askLlmForRotationOnce(b64);
            log.debug("[FUNCAPTCHA-ROTATE] Pass {}/{} rotation={} job={}",
                    i + 1, VOTING_PASSES, r, jobId);
            if (r != null) rotations.add(r);
        }
        if (rotations.isEmpty()) return null;

        Collections.sort(rotations);
        int median = rotations.get(rotations.size() / 2);
        log.info("[FUNCAPTCHA-ROTATE] Rotation votes={} median={} job={}",
                rotations, median, jobId);
        return median;
    }

    private Integer askLlmForRotationOnce(String b64) {
        try {
            return doRotationLlmCall(b64);
        } catch (LlmRateLimitException rate) {
            log.warn("[FUNCAPTCHA-ROTATE] Groq rate limit — backing off {}ms",
                    rate.getRetryAfter().toMillis());
            try {
                Thread.sleep(rate.getRetryAfter().toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
            try {
                return doRotationLlmCall(b64);
            } catch (Exception retryFailed) {
                log.warn("[FUNCAPTCHA-ROTATE] LLM still failed after backoff: {}",
                        retryFailed.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.warn("[FUNCAPTCHA-ROTATE] LLM rotation call failed: {}", e.getMessage());
            return null;
        }
    }

    private Integer doRotationLlmCall(String b64) {
        String prompt = """
                You are solving a FunCaptcha (Arkose Labs) rotate puzzle.
                
                The supplied image is a 3D-rendered object or animal that has been
                rotated around its centre (in the plane of the screen) from its
                natural upright position. Your job is to estimate the CURRENT
                rotation angle relative to upright.
                
                ORIENTATION REFERENCE (upright = 0°):
                  - Animals: standing on legs, head up, facing the viewer or in profile
                  - Objects: in their natural everyday position (e.g., a clock with 12 at the top,
                    a chair with seat up and legs down, a key with the head up and teeth down)
                
                CONVENTION:
                  - rotation_degrees > 0  ⇒ the image has been rotated CLOCKWISE from upright
                    (example: a dog lying on its right side, head pointing right = +90°)
                  - rotation_degrees < 0  ⇒ the image has been rotated COUNTER-CLOCKWISE
                    (example: a dog lying on its left side, head pointing left = -90°)
                  - rotation_degrees = 0  ⇒ already upright (no rotation needed)
                  - Use the shorter signed value: report -90 rather than +270
                
                Allowed range: integer between -180 and 180.
                
                LOOK CAREFULLY:
                  - Locate the natural "top" of the object (animal head, clock 12, key bow, etc.)
                  - Locate the natural "bottom" (feet, legs, base)
                  - The angle from the natural top to the current visual top, measured
                    clockwise, IS your rotation_degrees.
                
                Respond with ONLY a JSON object (no markdown, no prose):
                {
                  "rotation_degrees": <-180..180>,
                  "confidence": <0.0-1.0>,
                  "reasoning": "<brief: what object, where its natural top points now>"
                }
                """;

        String raw = visionClient.analyzeImage(prompt, b64, "image/png");
        List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
        if (parsed.isEmpty()) {
            log.warn("[FUNCAPTCHA-ROTATE] LLM returned empty parse result");
            return null;
        }

        Map<String, Object> first = parsed.getFirst();
        Object rObj = first.get("rotation_degrees");

        log.info("[FUNCAPTCHA-ROTATE] LLM rotation={} reasoning='{}' confidence={}",
                rObj, first.get("reasoning"), first.get("confidence"));

        if (rObj instanceof Number n) {
            return normalize(n.intValue());
        }
        if (rObj != null) {
            try {
                return normalize(Integer.parseInt(rObj.toString().trim()));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Wrap into (-180, 180].
     */
    private static int normalize(int deg) {
        while (deg > 180) deg -= 360;
        while (deg <= -180) deg += 360;
        return deg;
    }
}