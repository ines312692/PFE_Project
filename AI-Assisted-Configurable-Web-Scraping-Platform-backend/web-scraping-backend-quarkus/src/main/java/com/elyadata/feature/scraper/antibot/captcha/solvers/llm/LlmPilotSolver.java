package com.elyadata.feature.scraper.antibot.captcha.solvers.llm;

import com.elyadata.common.llm.LlmRateLimitException;
import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.VisionLlmClient;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LlmPilotSolver {

    private static final int POST_ACTION_WAIT_MS = 2_500;
    private static final int BETWEEN_DRAG_STEPS = 25;
    private static final double SUCCESS_CONFIDENCE_THRESHOLD = 0.6;
    private static final int MAX_UNCHANGED_STREAK = 2;

    private static final String PROMPT_BASE = """
            You are guiding a browser automation agent through an anti-bot challenge.
            
            You see a SCREENSHOT of the current page. Decide the SINGLE next action
            to make progress toward solving the challenge.
            
            Available actions:
            - "click"   : click a visible element. Provide pixel coordinates.
            - "drag"    : drag from a start point to an end point (for sliders).
            - "type"    : type text into the currently focused input.
            - "done"    : the challenge is solved or no challenge is visible.
            - "give_up" : the challenge cannot be solved automatically.
            
            CRITICAL rules:
            - Coordinates are in PIXELS from the top-left of the screenshot.
            - For "click", only click on visible interactive elements (buttons,
              checkboxes, links). Never click in empty space.
            - For "drag", provide both start (x,y) and end (x,y).
            
            SPECIAL CASE — Cloudflare passive interstitial:
            If you see "Verifying you are human", "Checking your browser",
            "Just a moment...", or a Cloudflare logo with NO clickable button
            or checkbox visible, respond with "done" immediately. These pages
            resolve automatically without any user action.
            
            SPECIAL CASE — Page already solved:
            If the page looks normal (article, product list, search results,
            login form, normal website content) with no anti-bot challenge
            visible, respond with "done".
            
            Respond with ONLY a JSON object, no markdown:
            {"action": "click|drag|type|done|give_up",
             "target": {"x": <int>, "y": <int>},
             "drag_to": {"x": <int>, "y": <int>},
             "text": "<string for type action>",
             "confidence": <0.0-1.0>,
             "reasoning": "<one short sentence>"}
            
            Only include fields relevant to the chosen action.
            """;

    private final VisionLlmClient visionClient;

    @ConfigProperty(name = "captcha.llm-pilot.max-iterations", defaultValue = "4")
    int maxIterations;

    @ConfigProperty(name = "captcha.llm-pilot.enabled", defaultValue = "true")
    boolean enabled;


    public boolean solve(Page page, UUID jobId) {
        if (!enabled) {
            log.debug("[LLM-PILOT] Disabled — skipping job={}", jobId);
            return false;
        }
        if (!visionClient.isAvailable()) {
            log.warn("[LLM-PILOT] Vision client unavailable — skipping job={}", jobId);
            return false;
        }

        log.info("[LLM-PILOT] Starting vision-action loop job={} (max iterations={})",
                jobId, maxIterations);

        List<String> history = new ArrayList<>();
        String prevHash = null;
        int unchangedStreak = 0;

        for (int i = 1; i <= maxIterations; i++) {
            log.info("[LLM-PILOT] Iteration {}/{} job={}", i, maxIterations, jobId);

            byte[] screenshot;
            try {
                screenshot = page.screenshot();
            } catch (Exception e) {
                log.warn("[LLM-PILOT] Screenshot failed: {}", e.getMessage());
                return false;
            }
            if (screenshot == null || screenshot.length < 1_000) {
                log.warn("[LLM-PILOT] Screenshot too small — aborting iteration");
                return false;
            }

            String currentHash = sha1(screenshot);

            // Annotate last history entry with the outcome of its action
            if (prevHash != null && !history.isEmpty()) {
                boolean changed = !currentHash.equals(prevHash);
                String last = history.remove(history.size() - 1);
                history.add(last + (changed ? " → page changed" : " → NO change"));

                if (!changed) {
                    unchangedStreak++;
                    if (unchangedStreak >= MAX_UNCHANGED_STREAK) {
                        log.warn("[LLM-PILOT] Page unchanged for {} consecutive actions — " +
                                "giving up to avoid burning tokens job={}", unchangedStreak, jobId);
                        return false;
                    }
                } else {
                    unchangedStreak = 0;
                }
            }
            prevHash = currentHash;

            Optional<Decision> decision = askLlmForNextAction(screenshot, history, jobId);
            if (decision.isEmpty()) {
                log.warn("[LLM-PILOT] No usable decision from LLM — stopping job={}", jobId);
                return false;
            }

            Decision d = decision.get();
            log.info("[LLM-PILOT] Decision: action={} confidence={} reasoning='{}' job={}",
                    d.action, d.confidence, d.reasoning, jobId);

            switch (d.action) {
                case "done" -> {
                    if (d.confidence >= SUCCESS_CONFIDENCE_THRESHOLD) {
                        log.info("[LLM-PILOT] Challenge marked as solved by LLM job={}", jobId);
                        return true;
                    }
                    log.warn("[LLM-PILOT] LLM said 'done' but confidence={} too low — stopping",
                            d.confidence);
                    return false;
                }
                case "give_up" -> {
                    log.warn("[LLM-PILOT] LLM gave up: '{}' job={}", d.reasoning, jobId);
                    return false;
                }
                case "click" -> {
                    if (!executeClick(page, d, jobId)) return false;
                    history.add(String.format("Click at (%d,%d)", d.targetX, d.targetY));
                }
                case "drag" -> {
                    if (!executeDrag(page, d, jobId)) return false;
                    history.add(String.format("Drag (%d,%d)→(%d,%d)",
                            d.targetX, d.targetY, d.dragToX, d.dragToY));
                }
                case "type" -> {
                    if (!executeType(page, d, jobId)) return false;
                    history.add(String.format("Type '%s'",
                            d.text.length() > 20 ? d.text.substring(0, 20) + "..." : d.text));
                }
                default -> {
                    log.warn("[LLM-PILOT] Unknown action '{}' — stopping job={}", d.action, jobId);
                    return false;
                }
            }

            page.waitForTimeout(POST_ACTION_WAIT_MS);
        }

        log.warn("[LLM-PILOT] Exhausted {} iterations without resolution job={}",
                maxIterations, jobId);
        return false;
    }

    private Optional<Decision> askLlmForNextAction(byte[] screenshot,
                                                   List<String> history,
                                                   UUID jobId) {
        try {
            String b64 = Base64.getEncoder().encodeToString(screenshot);
            String prompt = buildPromptWithHistory(history);

            String raw = visionClient.analyzeImage(prompt, b64, "image/png");
            List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
            if (parsed.isEmpty()) {
                log.warn("[LLM-PILOT] LLM returned empty parse result");
                return Optional.empty();
            }

            return Optional.of(parseDecision(parsed.getFirst()));

        } catch (LlmRateLimitException rate) {
            log.warn("[LLM-PILOT] Rate limited — aborting loop for job={}", jobId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[LLM-PILOT] LLM call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String buildPromptWithHistory(List<String> history) {
        if (history.isEmpty()) return PROMPT_BASE;
        StringBuilder sb = new StringBuilder(PROMPT_BASE);
        sb.append("\n\nPREVIOUS ACTIONS YOU ALREADY TRIED THIS SESSION:\n");
        for (int i = 0; i < history.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(history.get(i)).append("\n");
        }
        sb.append("""
                
                IMPORTANT — Learning from history:
                - If a previous action shows "NO change", that approach IS NOT WORKING.
                  Do NOT repeat the same action or click the same area again.
                - Either try a clearly DIFFERENT element/location, OR respond "give_up".
                - If 2+ actions in a row showed "NO change", you MUST give_up.
                """);
        return sb.toString();
    }

    private static Decision parseDecision(Map<String, Object> raw) {
        Decision d = new Decision();
        d.action = asString(raw.get("action"), "give_up").toLowerCase(Locale.ROOT);
        d.confidence = asDouble(raw.get("confidence"));
        d.reasoning = asString(raw.get("reasoning"), "");
        d.text = asString(raw.get("text"), "");

        Object target = raw.get("target");
        if (target instanceof Map<?, ?> m) {
            d.targetX = asInt(m.get("x"));
            d.targetY = asInt(m.get("y"));
        }

        Object dragTo = raw.get("drag_to");
        if (dragTo instanceof Map<?, ?> m) {
            d.dragToX = asInt(m.get("x"));
            d.dragToY = asInt(m.get("y"));
        }
        return d;
    }

    private boolean executeClick(Page page, Decision d, UUID jobId) {
        if (d.targetX < 0 || d.targetY < 0) {
            log.warn("[LLM-PILOT] Invalid click target ({},{}) — stopping job={}",
                    d.targetX, d.targetY, jobId);
            return false;
        }
        try {
            log.info("[LLM-PILOT] Click at ({}, {}) job={}", d.targetX, d.targetY, jobId);
            page.mouse().move(d.targetX, d.targetY);
            page.waitForTimeout(150);
            page.mouse().click(d.targetX, d.targetY);
            return true;
        } catch (Exception e) {
            log.warn("[LLM-PILOT] Click failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean executeDrag(Page page, Decision d, UUID jobId) {
        if (d.targetX < 0 || d.dragToX < 0) {
            log.warn("[LLM-PILOT] Invalid drag coordinates — stopping job={}", jobId);
            return false;
        }
        try {
            log.info("[LLM-PILOT] Drag from ({},{}) to ({},{}) job={}",
                    d.targetX, d.targetY, d.dragToX, d.dragToY, jobId);
            page.mouse().move(d.targetX, d.targetY);
            page.waitForTimeout(120);
            page.mouse().down();
            page.waitForTimeout(80);
            page.mouse().move(d.dragToX, d.dragToY,
                    new com.microsoft.playwright.Mouse.MoveOptions().setSteps(BETWEEN_DRAG_STEPS));
            page.waitForTimeout(120);
            page.mouse().up();
            return true;
        } catch (Exception e) {
            log.warn("[LLM-PILOT] Drag failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean executeType(Page page, Decision d, UUID jobId) {
        if (d.text == null || d.text.isBlank()) {
            log.warn("[LLM-PILOT] Type action with empty text — stopping job={}", jobId);
            return false;
        }
        try {
            log.info("[LLM-PILOT] Type '{}' (len={}) job={}",
                    d.text.length() > 20 ? d.text.substring(0, 20) + "..." : d.text,
                    d.text.length(), jobId);
            page.keyboard().type(d.text);
            return true;
        } catch (Exception e) {
            log.warn("[LLM-PILOT] Type failed: {}", e.getMessage());
            return false;
        }
    }

    private static String sha1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is guaranteed available on every JVM
            return String.valueOf(data.length);
        }
    }

    private static String asString(Object v, String fallback) {
        return v == null ? fallback : v.toString();
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return -1;
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static class Decision {
        String action;
        double confidence;
        String reasoning;
        String text;
        int targetX = -1;
        int targetY = -1;
        int dragToX = -1;
        int dragToY = -1;
    }
}