package com.elyadata.feature.scraper.antibot.captcha.solvers.geetest;

import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.solvers.llm.GeetestPromptFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class GeetestSliderSolver {

    private static final int MAX_PUZZLE_ATTEMPTS = 3;
    private static final int SCAN_POSITIONS = 9;
    private static final int SCAN_STEP_PIXELS = 12;
    private static final int SUCCESS_CHECK_TIMEOUT_MS = 3_000;
    private static final int SUCCESS_POLL_INTERVAL_MS = 500;
    private static final int PRE_INTERACT_MIN_MS = 2_000;
    private static final int PRE_INTERACT_JITTER_MS = 1_500;

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private static final String GEETEST_WINDOW = ".geetest_window";
    private static final String MIME_PNG = "image/png";

    private final GeetestPromptFactory prompts;
    private final GeetestImageAnalyzer imageAnalyzer;
    private final VisionLlmClient visionClient;

    public boolean solve(Page page, UUID jobId) {
        try {
            log.info("[GEETEST] Starting solve job={} (OpenCV ready={})",
                    jobId, imageAnalyzer.isReady());

            page.waitForTimeout(PRE_INTERACT_MIN_MS
                    + (double) ThreadLocalRandom.current().nextInt(0, PRE_INTERACT_JITTER_MS));

            triggerPuzzleIfNeeded(page, jobId);

            for (int attempt = 1; attempt <= MAX_PUZZLE_ATTEMPTS; attempt++) {
                log.info("[GEETEST] Puzzle attempt {}/{} job={}",
                        attempt, MAX_PUZZLE_ATTEMPTS, jobId);

                page.waitForTimeout(3_000);

                if (!isPuzzleVisible(page)) {
                    log.warn("[GEETEST] Puzzle not visible on attempt {} job={}", attempt, jobId);
                    return false;
                }

                if (solveOneAttempt(page, jobId)) {
                    log.info("[GEETEST] Solved on attempt {} job={}", attempt, jobId);
                    return true;
                }

                if (attempt < MAX_PUZZLE_ATTEMPTS) {
                    refreshPuzzle(page, jobId);
                }
            }

            log.warn("[GEETEST] All {} attempts failed job={}", MAX_PUZZLE_ATTEMPTS, jobId);
            return false;

        } catch (Exception e) {
            log.warn("[GEETEST] Solve failed job={}: {}", jobId, e.getMessage());
            return false;
        }
    }

    private boolean solveOneAttempt(Page page, UUID jobId) {
        byte[] initialShot = screenshotPuzzle(page);
        if (initialShot == null) {
            log.warn("[GEETEST] Could not screenshot puzzle job={}", jobId);
            return false;
        }

        Optional<GeetestImageAnalyzer.DetectionResult> cv =
                imageAnalyzer.detectHolePosition(initialShot);

        if (cv.isPresent()) {
            return solveWithOpenCV(page, jobId, cv.get());
        }

        log.info("[GEETEST] OpenCV returned no position  falling back to LLM job={}", jobId);
        return solveWithLlm(page, jobId, initialShot);
    }


    private boolean solveWithOpenCV(Page page, UUID jobId, GeetestImageAnalyzer.DetectionResult cv) {
        int offset = cv.sliderOffsetPx();
        log.info("[GEETEST] OpenCV detected: offset={}px score={} job={}",
                offset, String.format("%.3f", cv.correlationScore()), jobId);

        if (!performHumanlikeDrag(page, offset, jobId)) {
            return false;
        }
        if (checkSuccess(page)) {
            log.info("[GEETEST] OpenCV solved on first drag (offset={}px) job={}", offset, jobId);
            return true;
        }

        log.info("[GEETEST] OpenCV drag missed — entering progressive micro-corrections job={}",
                jobId);
        page.waitForTimeout(1_000);

        int[] deltas = {-3, 3, -6, 6, -10, 10, -15, 15, -20, 20, -25, 25, -30, 30, -35, 35};

        for (int delta : deltas) {
            int adjusted = offset + delta;
            if (adjusted <= 0) continue;
            if (!performHumanlikeDrag(page, adjusted, jobId)) return false;
            if (checkSuccess(page)) {
                log.info("[GEETEST] OpenCV solved with correction Δ={}px (final={}px) job={}",
                        delta, adjusted, jobId);
                return true;
            }
            page.waitForTimeout(600);
        }
        log.warn("[GEETEST] All OpenCV corrections exhausted job={}", jobId);
        return false;
    }


    private boolean solveWithLlm(Page page, UUID jobId, byte[] initialShot) {
        int puzzleWidth = getPuzzleWidth(page);
        log.info("[GEETEST] Puzzle width: {}px job={}", puzzleWidth, jobId);

        String initialB64 = Base64.getEncoder().encodeToString(initialShot);
        int rawPercent = askLlmForDistance(initialB64);

        int workingPercent = (rawPercent < 25 || rawPercent > 90) ? 60 : rawPercent;
        if (rawPercent < 25 || rawPercent > 90) {
            log.warn("[GEETEST] LLM percent {}% out of range — using fallback=60% job={}",
                    rawPercent, jobId);
        }

        int sliderTravelArea = puzzleWidth - 50;
        int scaledOffset = (int) (sliderTravelArea * (workingPercent / 100.0));

        log.info("[GEETEST] LLM={}% → working={}% → offset={}px (travel-area={}px) job={}",
                rawPercent, workingPercent, scaledOffset, sliderTravelArea, jobId);

        if (!performHumanlikeDrag(page, scaledOffset, jobId)) return false;
        if (checkSuccess(page)) {
            log.info("[GEETEST] LLM solved on first drag (offset={}px) job={}", scaledOffset, jobId);
            return true;
        }

        log.info("[GEETEST] First drag missed, entering fine-grained scan job={}", jobId);
        page.waitForTimeout(1_500);

        byte[] correctionShot = screenshotPuzzle(page);
        if (correctionShot == null) return false;

        int direction = askLlmForDirection(scaledOffset,
                Base64.getEncoder().encodeToString(correctionShot));
        log.info("[GEETEST] Correction direction={} job={}", direction > 0 ? "+" : "-", jobId);

        List<ScanResult> scans = performScan(page, scaledOffset, jobId);
        if (scans.isEmpty()) {
            log.warn("[GEETEST] Scan produced no screenshots job={}", jobId);
            return false;
        }
        log.info("[GEETEST] Captured {} scan positions: {} job={}",
                scans.size(),
                scans.stream().map(s -> s.pixelPosition() + "px").toList(),
                jobId);

        int bestFitIndex = askLlmForBestFit(scans, jobId);
        if (bestFitIndex < 0 || bestFitIndex >= scans.size()) {
            log.warn("[GEETEST] LLM returned invalid best-fit index={} (max={}) job={}",
                    bestFitIndex, scans.size() - 1, jobId);
            return false;
        }

        int finalOffset = scans.get(bestFitIndex).pixelPosition();
        log.info("[GEETEST] LLM chose scan index={} → final offset={}px job={}",
                bestFitIndex, finalOffset, jobId);

        if (!performHumanlikeDrag(page, finalOffset, jobId)) return false;
        return checkSuccess(page);
    }

    private int getPuzzleWidth(Page page) {
        try {
            Locator puzzle = page.locator(GEETEST_WINDOW).first();
            BoundingBox box = puzzle.boundingBox();
            if (box != null && box.width > 0) {
                return (int) box.width;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return 280;
    }

    private boolean performHumanlikeDrag(Page page, int offsetPx, UUID jobId) {
        try {
            Locator slider = page.locator(".geetest_slider_button").first();
            slider.waitFor(new Locator.WaitForOptions().setTimeout(5_000));
            BoundingBox box = slider.boundingBox();
            if (box == null) {
                log.warn("[GEETEST] Slider bounding box null job={}", jobId);
                return false;
            }

            double startX = box.x + box.width / 2.0;
            double startY = box.y + box.height / 2.0;

            Mouse mouse = page.mouse();

            mouse.move(startX - 5, startY - 2, new Mouse.MoveOptions().setSteps(10));
            sleepMs(150 + (long) ThreadLocalRandom.current().nextInt(0, 200));
            mouse.move(startX, startY, new Mouse.MoveOptions().setSteps(5));
            sleepMs(80 + (long) ThreadLocalRandom.current().nextInt(0, 120));

            mouse.down();
            sleepMs(180 + (long) ThreadLocalRandom.current().nextInt(0, 250));

            int totalSteps = 40 + ThreadLocalRandom.current().nextInt(0, 20);
            for (int i = 1; i <= totalSteps; i++) {
                double t = (double) i / totalSteps;
                double progress = 1 - Math.pow(1 - t, 3);

                double currentX = startX + (offsetPx * progress);
                double yJitter = Math.sin(i * 0.4) * 1.5
                        + ThreadLocalRandom.current().nextDouble(-0.8, 0.8);

                mouse.move(currentX, startY + yJitter,
                        new Mouse.MoveOptions().setSteps(1));

                int microDelay;
                if (i < totalSteps * 0.3) {
                    microDelay = 8 + ThreadLocalRandom.current().nextInt(0, 8);
                } else if (i < totalSteps * 0.8) {
                    microDelay = 12 + ThreadLocalRandom.current().nextInt(0, 10);
                } else {
                    microDelay = 25 + ThreadLocalRandom.current().nextInt(0, 20);
                }
                sleepMs(microDelay);
            }
            sleepMs(220 + (long) ThreadLocalRandom.current().nextInt(0, 200));
            mouse.up();
            return true;

        } catch (Exception e) {
            log.warn("[GEETEST] Drag failed: {} job={}", e.getMessage(), jobId);
            return false;
        }
    }

    private List<ScanResult> performScan(Page page, int baseOffset, UUID jobId) {
        List<ScanResult> results = new ArrayList<>();
        try {
            Locator slider = page.locator(".geetest_slider_button").first();
            BoundingBox box = slider.boundingBox();
            if (box == null) return results;

            double startX = box.x + box.width / 2.0;
            double startY = box.y + box.height / 2.0;

            Mouse mouse = page.mouse();
            mouse.move(startX, startY);
            mouse.down();
            sleepMs(100);

            int halfSpan = (SCAN_POSITIONS / 2) * SCAN_STEP_PIXELS;
            int startPos = baseOffset - halfSpan;

            for (int i = 0; i < SCAN_POSITIONS; i++) {
                int pos = startPos + (i * SCAN_STEP_PIXELS);
                if (pos < 0) continue;

                mouse.move(startX + pos, startY,
                        new Mouse.MoveOptions().setSteps(3));
                sleepMs(200);

                byte[] shot = screenshotPuzzle(page);
                if (shot != null) {
                    results.add(new ScanResult(pos,
                            Base64.getEncoder().encodeToString(shot)));
                }
            }

            mouse.move(startX, startY, new Mouse.MoveOptions().setSteps(5));
            sleepMs(100);
            mouse.up();

        } catch (Exception e) {
            log.warn("[GEETEST] Scan failed: {} job={}", e.getMessage(), jobId);
            try {
                page.mouse().up();
            } catch (Exception ignored) {
                // ignore
            }
        }
        return results;
    }

    private int askLlmForDistance(String puzzleB64) {
        try {
            String raw = visionClient.analyzeImage(prompts.distancePrompt(), puzzleB64, MIME_PNG);
            log.info("[GEETEST-LLM] Raw distance response: '{}'", raw);
            return parseInteger(raw);
        } catch (Exception e) {
            log.warn("[GEETEST-LLM] Distance call failed: {}", e.getMessage());
            return -1;
        }
    }

    private int askLlmForDirection(int currentOffset, String correctionB64) {
        if (currentOffset < 50) return 1;
        if (currentOffset > 250) return -1;

        try {
            String raw = visionClient.analyzeImage(
                    prompts.correctionDirectionPrompt(), correctionB64, MIME_PNG);
            log.debug("[GEETEST-LLM] Raw direction response: '{}'", raw);
            return raw != null && raw.contains("+") ? 1 : -1;
        } catch (Exception e) {
            log.warn("[GEETEST-LLM] Direction call failed: {} — defaulting to +", e.getMessage());
            return 1;
        }
    }

    private int askLlmForBestFit(List<ScanResult> scans, UUID jobId) {
        if (scans.isEmpty()) return -1;
        if (scans.size() == 1) return 0;

        try {
            byte[] composedBytes = composeScanGrid(scans);

            String composedB64 = Base64.getEncoder().encodeToString(composedBytes);
            String raw = visionClient.analyzeImage(
                    prompts.bestFitPrompt(scans.size()), composedB64, MIME_PNG);
            log.info("[GEETEST-LLM] Raw best-fit response: '{}'", raw);

            int idx = parseIndexFromResponse(raw, scans.size() - 1);
            if (idx < 0) {
                log.warn("[GEETEST] LLM best-fit unparseable, falling back to middle job={}", jobId);
                return scans.size() / 2;
            }
            return idx;

        } catch (Exception e) {
            log.warn("[GEETEST] Best-fit LLM call failed: {} — using middle index job={}",
                    e.getMessage(), jobId);
            return scans.size() / 2;
        }
    }

    private static int parseIndexFromResponse(String raw, int maxIndex) {
        if (raw == null) return -1;

        Pattern finalAnswerPattern = Pattern.compile(
                "FINAL[\\s_]ANSWER\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher fa = finalAnswerPattern.matcher(raw);
        if (fa.find()) {
            try {
                int idx = Integer.parseInt(fa.group(1));
                if (idx >= 0 && idx <= maxIndex) return idx;
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }

        Matcher m = Pattern.compile("\\d+").matcher(raw);
        while (m.find()) {
            try {
                int candidate = Integer.parseInt(m.group());
                if (candidate >= 0 && candidate <= maxIndex) {
                    return candidate;
                }
            } catch (NumberFormatException ignored) {
                //ignore
            }
        }
        return -1;
    }

    private byte[] composeScanGrid(List<ScanResult> scans) {
        try {
            List<BufferedImage> imgs = new ArrayList<>();
            for (ScanResult s : scans) {
                byte[] decoded = Base64.getDecoder().decode(s.screenshotB64());
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(decoded));
                if (bi != null) imgs.add(bi);
            }
            if (imgs.isEmpty()) return new byte[0];

            int unitW = imgs.getFirst().getWidth();
            int unitH = imgs.getFirst().getHeight();
            int totalW = unitW * imgs.size() + (imgs.size() - 1) * 10;

            BufferedImage composed = new BufferedImage(totalW, unitH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = composed.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, totalW, unitH);

            for (int i = 0; i < imgs.size(); i++) {
                int x = i * (unitW + 10);
                g.drawImage(imgs.get(i), x, 0, null);

                g.setColor(Color.RED);
                g.fillRect(x + 4, 4, 36, 28);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 20));
                g.drawString(String.valueOf(i), x + 14, 26);
            }
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(composed, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("[GEETEST] composeScanGrid failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    private void triggerPuzzleIfNeeded(Page page, UUID jobId) {
        try {
            Locator startBtn = page.locator(".geetest_radar_tip").first();
            if (startBtn.isVisible()) {
                log.info("[GEETEST] Clicking start button job={}", jobId);
                startBtn.click();
                page.waitForTimeout(1_500);
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private boolean isPuzzleVisible(Page page) {
        try {
            return page.locator(GEETEST_WINDOW).first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] screenshotPuzzle(Page page) {
        try {
            Locator puzzle = page.locator(GEETEST_WINDOW).first();
            return puzzle.screenshot();
        } catch (Exception e) {
            log.warn("[GEETEST] Screenshot failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    private void refreshPuzzle(Page page, UUID jobId) {
        try {
            String[] selectors = {".geetest_refresh_1", ".geetest_refresh", ".geetest_reset_tip_content"};
            for (String sel : selectors) {
                Locator refresh = page.locator(sel).first();
                if (refresh.isVisible()) {
                    refresh.click();
                    log.info("[GEETEST] Puzzle refreshed via '{}' job={}", sel, jobId);
                    page.waitForTimeout(2_500);
                    return;
                }
            }
            log.warn("[GEETEST] No refresh button found job={}", jobId);
        } catch (Exception e) {
            log.warn("[GEETEST] Could not refresh puzzle: {}", e.getMessage());
        }
    }

    private boolean checkSuccess(Page page) {
        long deadline = System.currentTimeMillis() + SUCCESS_CHECK_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Locator successEl = page.locator(".geetest_success_radar_tip_content").first();
                if (successEl.isVisible()) {
                    String text = successEl.innerText();
                    if (text != null && (text.toLowerCase().contains("success")
                            || text.toLowerCase().contains("succès")
                            || text.toLowerCase().contains("validé"))) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
            page.waitForTimeout(SUCCESS_POLL_INTERVAL_MS);
        }
        return false;
    }

    private static int parseInteger(String raw) {
        if (raw == null) return -1;
        Pattern finalAnswerPattern = Pattern.compile(
                "FINAL[\\s_]ANSWER\\s*:?\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher fa = finalAnswerPattern.matcher(raw);
        if (fa.find()) {
            try {
                return Integer.parseInt(fa.group(1));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }

        Matcher m = INTEGER_PATTERN.matcher(raw);
        int last = -1;
        while (m.find()) {
            try {
                int candidate = Integer.parseInt(m.group());
                if (candidate >= 30 && candidate <= 300) {
                    last = candidate;
                }
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return last;
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record ScanResult(int pixelPosition, String screenshotB64) {
    }
}