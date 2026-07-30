package com.elyadata.feature.scraper.antibot.captcha.solvers.recaptcha;

import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.solvers.support.ImageGridSolverSupport;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RecaptchaV2ImageSolver {

    private static final int MAX_ROUNDS = 4;
    private static final int CHALLENGE_WAIT_MS = 3_000;
    private static final int POST_VERIFY_WAIT_MS = 4_000;

    private static final int MAX_DYNAMIC_ITERATIONS = 6;
    private static final int DYNAMIC_FADE_WAIT_MS = 2_800;
    private static final int FADE_JITTER_MS = 600;

    private static final int PRE_CHECKBOX_MIN_MS = 2_000;
    private static final int PRE_CHECKBOX_JITTER_MS = 2_000;
    private static final int DYNAMIC_CLICK_DELAY_MIN_MS = 1_800;
    private static final int DYNAMIC_CLICK_DELAY_JITTER_MS = 600;
    private static final int STATIC_CLICK_DELAY_MIN_MS = 350;
    private static final int STATIC_CLICK_DELAY_JITTER_MS = 250;

    private static final int MAX_CLICKS_3X3 = 5;
    private static final int MAX_CLICKS_4X4 = 7;

    private static final String SELECTED_CLASS = "rc-imageselect-tileselected";
    private static final double PER_TILE_CONFIDENCE_THRESHOLD = 0.65;
    private static final int CLASSIFY_MAX_RETRIES = 2;
    private static final int INTER_TILE_DELAY_MS = 1_800;
    private static final long RATE_LIMIT_FALLBACK_WAIT_MS = 4_000;
    private static final long RATE_LIMIT_SAFETY_PAD_MS = 250;
    private static final long RATE_LIMIT_MAX_WAIT_MS = 30_000;
    private static final long RATE_LIMIT_MIN_WAIT_MS = 500;

    private static final Pattern RATE_LIMIT_WAIT_PATTERN =
            Pattern.compile("try again in ([0-9.]+)s", Pattern.CASE_INSENSITIVE);

    private static final String PER_TILE_PROMPT_TEMPLATE = """
            The original CAPTCHA challenge instruction is: "%s"
            
            You are looking at ONE small tile cropped from a 3x3 or 4x4 grid.
            You do NOT see the other tiles. There is NO indexing or counting involved.
            
            Decide ONLY: does THIS tile clearly contain the subject described in the challenge?
            
            Rules:
            - YES if the subject is clearly visible — even a partial view counts if recognizable.
            - NO for pure background (sky, road, water, grass, walls), empty areas, or
              ambiguous content where the subject is not clearly identifiable.
            - NO if you are unsure — be strict on borderline cases.
            - Do NOT speculate about what the tile "might be" in context — only what is visible.
            
            Respond with ONLY this JSON (no markdown, no prose):
            {"yes": true|false, "confidence": 0.0-1.0}
            """;

    private final ImageGridSolverSupport support;
    private final RecaptchaTileAnalyzer tileAnalyzer;
    private final VisionLlmClient visionClient;

    public Optional<String> solve(Page page, UUID jobId) {
        try {
            log.info("[RECAPTCHA-V2] Starting solve job={} (CV ready={})",
                    jobId, tileAnalyzer.isReady());
            page.waitForTimeout(PRE_CHECKBOX_MIN_MS
                    + ThreadLocalRandom.current().nextInt(0, PRE_CHECKBOX_JITTER_MS));

            FrameLocator anchor = page.frameLocator("iframe[src*='recaptcha/api2/anchor']").first();
            Locator checkbox = anchor.locator("#recaptcha-anchor");
            checkbox.waitFor();
            support.clickHumanlike(page, checkbox);

            page.waitForTimeout(CHALLENGE_WAIT_MS);
            String token = readToken(page);
            if (token != null && !token.isBlank()) {
                log.info("[RECAPTCHA-V2] Solved instantly without challenge job={}", jobId);
                return Optional.of(token);
            }

            for (int round = 1; round <= MAX_ROUNDS; round++) {
                log.info("[RECAPTCHA-V2] Round {}/{} job={}", round, MAX_ROUNDS, jobId);
                if (!solveOneRound(page, jobId)) {
                    log.warn("[RECAPTCHA-V2] Round {} failed job={}", round, jobId);
                    return Optional.empty();
                }
                page.waitForTimeout(POST_VERIFY_WAIT_MS);

                token = readToken(page);
                if (token != null && !token.isBlank()) {
                    log.info("[RECAPTCHA-V2] Solved after {} round(s) job={}", round, jobId);
                    return Optional.of(token);
                }
            }

            log.warn("[RECAPTCHA-V2] Exhausted {} rounds job={} — Google likely flagged session",
                    MAX_ROUNDS, jobId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[RECAPTCHA-V2] Solve failed job={}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean solveOneRound(Page page, UUID jobId) {
        FrameLocator challenge = page.frameLocator("iframe[src*='recaptcha/api2/bframe']").first();
        Locator promptLoc = challenge.locator(
                ".rc-imageselect-desc-no-canonical, .rc-imageselect-desc");
        promptLoc.waitFor();
        String instruction = promptLoc.innerText().trim();
        Locator tiles = challenge.locator(".rc-imageselect-tile");
        int tileCount = tiles.count();
        int gridSize = tileCount == 16 ? 4 : 3;
        boolean dynamicHint = gridSize == 3 && (
                instruction.toLowerCase().contains("once there are none left")
                        || instruction.toLowerCase().contains("aucune")
                        || challenge.locator(".rc-imageselect-progress").count() > 0
        );

        log.info("[RECAPTCHA-V2] Challenge='{}' grid={}x{} dynamicHint={} job={}",
                instruction.replaceAll("\\s+", " "), gridSize, gridSize, dynamicHint, jobId);

        Locator imageTable = challenge.locator(
                "table.rc-imageselect-table-33, table.rc-imageselect-table-44").first();
        byte[] gridBytes = imageTable.screenshot();

        List<Integer> tileIndices = pickTiles(gridBytes, instruction, gridSize, jobId);

        if (tileIndices.isEmpty()) {
            log.info("[RECAPTCHA-V2] No tile picks — clicking Verify directly job={}", jobId);
            clickVerify(page, challenge);
            return true;
        }

        int maxClicks = gridSize == 3 ? MAX_CLICKS_3X3 : MAX_CLICKS_4X4;
        List<Integer> toClick = tileIndices.stream().limit(maxClicks).toList();
        if (toClick.size() < tileIndices.size()) {
            log.warn("[RECAPTCHA-V2] Limiting clicks from {} to {} (anti-flag cap) job={}",
                    tileIndices.size(), toClick.size(), jobId);
        }

        log.info("[RECAPTCHA-V2] Clicking tiles {} job={}", toClick, jobId);
        clickTiles(page, tiles, tileCount, toClick, dynamicHint);
        page.waitForTimeout(DYNAMIC_FADE_WAIT_MS);

        if (anyTileSelected(challenge, toClick)) {
            log.info("[RECAPTCHA-V2] Static grid — selection held, verifying directly job={}", jobId);
        } else {
            runDynamicLoop(page, jobId, tiles, imageTable, instruction, gridSize, tileCount);
        }

        clickVerify(page, challenge);
        return true;
    }

    private List<Integer> pickTiles(byte[] gridBytes, String instruction,
                                    int gridSize, UUID jobId) {
        if (tileAnalyzer.isReady() && visionClient.isAvailable()) {
            try {
                Optional<List<Integer>> perTilePicks =
                        perTileClassification(gridBytes, instruction, gridSize, jobId);
                if (perTilePicks.isPresent()) {
                    return perTilePicks.get();
                }
            } catch (Exception e) {
                log.warn("[RECAPTCHA-V2] Per-tile classification threw: {} — " +
                        "falling back to pure LLM job={}", e.getMessage(), jobId);
            }
        } else {
            log.info("[RECAPTCHA-V2] CV or LLM unavailable for per-tile path " +
                            "(cvReady={}, llmAvailable={}) — using pure LLM job={}",
                    tileAnalyzer.isReady(), visionClient.isAvailable(), jobId);
        }

        String b64 = Base64.getEncoder().encodeToString(gridBytes);
        List<Integer> llmPicks = support.askLlmForTilesWithVoting(instruction, b64, gridSize);
        log.info("[RECAPTCHA-V2] Pure LLM voting picks={} job={}", llmPicks, jobId);
        return llmPicks;
    }

    private Optional<List<Integer>> perTileClassification(byte[] gridBytes, String instruction,
                                                          int gridSize, UUID jobId) {
        List<byte[]> tiles = tileAnalyzer.cropTiles(gridBytes, gridSize);
        if (tiles.isEmpty()) {
            log.warn("[RECAPTCHA-V2] Tile cropping returned empty — falling back job={}", jobId);
            return Optional.empty();
        }

        String prompt = PER_TILE_PROMPT_TEMPLATE.formatted(
                instruction.replaceAll("\\s+", " ").trim());

        List<Integer> picks = new ArrayList<>();
        List<String> verdictLogs = new ArrayList<>();

        for (int i = 0; i < tiles.size(); i++) {
            String b64 = Base64.getEncoder().encodeToString(tiles.get(i));
            TileVerdict v = classifyOneTile(prompt, b64, i, jobId);
            verdictLogs.add(i + ":" + v);
            if (v.yes() && v.confidence() >= PER_TILE_CONFIDENCE_THRESHOLD) {
                picks.add(i);
            }
            if (i < tiles.size() - 1) {
                sleepMs(INTER_TILE_DELAY_MS);
            }
        }

        log.info("[RECAPTCHA-V2] Per-tile verdicts [{}] → picks={} (threshold={}) job={}",
                String.join(" ", verdictLogs), picks,
                PER_TILE_CONFIDENCE_THRESHOLD, jobId);
        return Optional.of(picks);
    }

    private TileVerdict classifyOneTile(String prompt, String b64, int tileIdx, UUID jobId) {
        for (int attempt = 0; attempt <= CLASSIFY_MAX_RETRIES; attempt++) {
            try {
                String raw = visionClient.analyzeImage(prompt, b64, "image/png");
                List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
                if (parsed.isEmpty()) {
                    log.debug("[RECAPTCHA-V2] Tile {} empty LLM parse job={}", tileIdx, jobId);
                    return new TileVerdict(false, 0.0);
                }
                Map<String, Object> obj = parsed.getFirst();
                boolean yes = Boolean.TRUE.equals(obj.get("yes"));
                double conf = toDouble(obj.get("confidence"));
                return new TileVerdict(yes, conf);

            } catch (Exception e) {
                long waitMs = extractRateLimitWaitMs(e);
                boolean canRetry = waitMs > 0 && attempt < CLASSIFY_MAX_RETRIES;
                if (canRetry) {
                    log.info("[RECAPTCHA-V2] Tile {} rate-limited — sleeping {}ms then retry {}/{} job={}",
                            tileIdx, waitMs, attempt + 1, CLASSIFY_MAX_RETRIES, jobId);
                    sleepMs(waitMs);
                    continue;
                }
                log.warn("[RECAPTCHA-V2] Tile {} classification failed (attempt {}/{}): {} job={}",
                        tileIdx, attempt + 1, CLASSIFY_MAX_RETRIES + 1,
                        summarizeError(e), jobId);
                return new TileVerdict(false, 0.0);
            }
        }
        return new TileVerdict(false, 0.0);
    }

    private void clickTiles(Page page, Locator tiles, int tileCount,
                            List<Integer> indices, boolean dynamicPacing) {
        for (int idx : indices) {
            if (idx < 0 || idx >= tileCount) continue;
            support.clickHumanlike(page, tiles.nth(idx));
            if (dynamicPacing) {
                page.waitForTimeout(DYNAMIC_CLICK_DELAY_MIN_MS
                        + ThreadLocalRandom.current().nextInt(0, DYNAMIC_CLICK_DELAY_JITTER_MS));
            } else {
                page.waitForTimeout(STATIC_CLICK_DELAY_MIN_MS
                        + ThreadLocalRandom.current().nextInt(0, STATIC_CLICK_DELAY_JITTER_MS));
            }
        }
    }

    private void runDynamicLoop(Page page, UUID jobId,
                                Locator tiles, Locator imageTable,
                                String instruction, int gridSize, int tileCount) {
        for (int iter = 1; iter <= MAX_DYNAMIC_ITERATIONS; iter++) {
            byte[] freshBytes = imageTable.screenshot();
            List<Integer> remaining = pickTiles(freshBytes, instruction, gridSize, jobId);

            if (remaining.isEmpty()) {
                log.info("[RECAPTCHA-V2] Dynamic grid cleared after {} iteration(s) job={}",
                        iter, jobId);
                return;
            }

            log.info("[RECAPTCHA-V2] Dynamic iter {}/{}: clicking {} job={}",
                    iter, MAX_DYNAMIC_ITERATIONS, remaining, jobId);
            clickTiles(page, tiles, tileCount, remaining, true);

            if (iter < MAX_DYNAMIC_ITERATIONS) {
                page.waitForTimeout(DYNAMIC_FADE_WAIT_MS
                        + ThreadLocalRandom.current().nextInt(0, FADE_JITTER_MS));
            } else {
                log.warn("[RECAPTCHA-V2] Dynamic grid not clear after {} iterations — "
                        + "clicks may not be registering job={}", MAX_DYNAMIC_ITERATIONS, jobId);
            }
        }
    }

    private void clickVerify(Page page, FrameLocator challenge) {
        Locator verify = challenge.locator("#recaptcha-verify-button");
        support.clickHumanlike(page, verify);
    }

    private boolean anyTileSelected(FrameLocator challenge, List<Integer> indices) {
        Locator tiles = challenge.locator(".rc-imageselect-tile");
        for (int idx : indices) {
            try {
                String cls = tiles.nth(idx).getAttribute("class");
                if (cls != null && cls.contains(SELECTED_CLASS)) return true;
            } catch (Exception ignored) {
                // tile not present / detached — treat as not selected
            }
        }
        return false;
    }

    private String readToken(Page page) {
        try {
            Object val = page.evaluate("""
                    () => {
                      const ta = document.querySelector(
                        'textarea[name="g-recaptcha-response"], #g-recaptcha-response');
                      return ta ? ta.value : '';
                    }
                    """);
            return val == null ? null : val.toString();
        } catch (Exception e) {
            return null;
        }
    }


    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return 0.0;
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }


    private static long extractRateLimitWaitMs(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return 0;
        boolean isRateLimit = msg.contains("rate_limit_exceeded")
                || msg.contains("Rate limit reached")
                || msg.contains("429");
        if (!isRateLimit) return 0;

        Matcher m = RATE_LIMIT_WAIT_PATTERN.matcher(msg);
        if (m.find()) {
            try {
                double secs = Double.parseDouble(m.group(1));
                long ms = (long) (secs * 1_000) + RATE_LIMIT_SAFETY_PAD_MS;
                return Math.max(RATE_LIMIT_MIN_WAIT_MS, Math.min(ms, RATE_LIMIT_MAX_WAIT_MS));
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return RATE_LIMIT_FALLBACK_WAIT_MS;
    }

    private static String summarizeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        int idx = msg.indexOf("\"message\":\"");
        if (idx >= 0) {
            int start = idx + "\"message\":\"".length();
            int end = msg.indexOf("\"", start);
            if (end > start) return msg.substring(start, Math.min(end, start + 140));
        }
        return msg.substring(0, Math.min(180, msg.length()));
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record TileVerdict(boolean yes, double confidence) {
        @Override
        public @NonNull String toString() {
            return (yes ? "Y" : "N") + String.format("(%.2f)", confidence);
        }
    }
}