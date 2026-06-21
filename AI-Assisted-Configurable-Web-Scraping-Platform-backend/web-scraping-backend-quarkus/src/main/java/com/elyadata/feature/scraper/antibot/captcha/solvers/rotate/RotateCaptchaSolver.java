package com.elyadata.feature.scraper.antibot.captcha.solvers.rotate;

import com.elyadata.common.llm.LlmRateLimitException;
import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.solvers.support.ImageGridSolverSupport;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NonNull;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class RotateCaptchaSolver {

    private static final int VOTING_PASSES = 5;
    private static final int MAX_CORRECTION_ROUNDS = 5;
    private static final int MAX_CLICKS_GUARD = 20;
    private static final int UPRIGHT_TOLERANCE_DEG = 12;
    private static final int FINE_REFINE_THRESHOLD_DEG = 45;
    private static final double MIN_VOTE_CONCENTRATION = 0.5;
    private static final int STUCK_LOOP_THRESHOLD = 3;
    private static final int CLICK_DELAY_MIN_MS = 260;
    private static final int CLICK_DELAY_JITTER_MS = 240;
    private static final int POST_ROTATE_WAIT_MS = 600;
    private static final int PUZZLE_PROBE_TIMEOUT_MS = 5_000;
    private static final int PRE_INTERACT_MIN_MS = 1_200;
    private static final int PRE_INTERACT_JITTER_MS = 1_300;
    private static final int SUCCESS_PROBE_TIMEOUT_MS = 3_000;
    private static final int POST_SUBMIT_WAIT_MS = 1_500;
    private static final int MIN_PUZZLE_SIZE_PX = 100;
    private static final double PUZZLE_CHANGE_THRESHOLD = 4.0;
    private static final int LLM_UPSCALE_PX = 480;
    private static final String TARGET_DIR = "target";
    private static final String CAPTCHA_DEBUG_DIR = "captcha-debug";
    private static final String MIME_PNG = "image/png";
    private static final String PUZZLE_IMAGE_SELECTOR =
            "img[src*='captcha'], " +
                    "[class*='rotate-captcha'] img, " +
                    "[class*='rotatecaptcha'] img, " +
                    "[class*='rotate'] img, " +
                    "[class*='rotate'] canvas, " +
                    "canvas[class*='captcha']";
    private static final String SUBMIT_SELECTOR =
            "button:has-text('Check'), " +
                    "button:has-text('Vérifier'), " +
                    "button:has-text('Verify'), " +
                    "button:has-text('Submit'), " +
                    "button[type='submit']";
    private static final String SUCCESS_SELECTOR =
            "[class*='success'], " +
                    "[class*='solved'], " +
                    "[class*='passed'], " +
                    "[class*='_success']";

    private final VisionLlmClient visionClient;
    @Inject
    ImageGridSolverSupport support;

    @ConfigProperty(name = "captcha.rotate.step-degrees", defaultValue = "20")
    int stepDegrees;

    @ConfigProperty(name = "captcha.rotate.debug-dump", defaultValue = "false")
    boolean debugDump;

    @ConfigProperty(name = "captcha.rotate.invert-sign", defaultValue = "false")
    boolean invertSign;

    private boolean opencvReady = false;

    private record ArrowCoords(double leftX, double leftY, double rightX, double rightY) {
    }

    private record RotationProbe(byte[] screenshotBytes, Integer rotationDegrees) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RotationProbe(byte[] otherBytes, Integer otherDeg))) return false;
            return Arrays.equals(screenshotBytes, otherBytes)
                    && Objects.equals(rotationDegrees, otherDeg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(screenshotBytes), rotationDegrees);
        }

        @Override
        public @NonNull String toString() {
            return "RotationProbe[screenshotBytes=" + Arrays.toString(screenshotBytes)
                    + ", rotationDegrees=" + rotationDegrees + "]";
        }
    }

    private record Candidate(Locator locator, BoundingBox box, double area) {
    }

    @PostConstruct
    void init() {
        try {
            OpenCV.loadLocally();
            opencvReady = true;
            log.info("[ROTATE] OpenCV native library loaded (version={})", Core.getVersionString());
        } catch (Throwable t) {
            log.error("[ROTATE] Failed to load OpenCV: {} — arrow detection disabled, "
                    + "rotate captcha will be unsolvable via this path", t.getMessage());
            opencvReady = false;
        }
    }

    public boolean isReady() {
        return opencvReady;
    }

    public boolean solve(Page page, UUID jobId) {
        try {
            log.info("[ROTATE] Starting solve job={} (step={}deg, OpenCV ready={})",
                    jobId, stepDegrees, opencvReady);
            sleep(PRE_INTERACT_MIN_MS + (long) rnd(PRE_INTERACT_JITTER_MS));

            List<Candidate> candidates = locatePuzzleCandidates(page, jobId);
            if (candidates.isEmpty()) {
                log.warn("[ROTATE] No puzzle candidate found job={}", jobId);
                return false;
            }

            Locator puzzle = null;
            ArrowCoords arrows = null;

            for (Candidate cand : candidates) {
                ArrowCoords found = findArrowsViaOpenCV(page, cand.box(), jobId);
                if (found != null) {
                    puzzle = cand.locator();
                    arrows = found;
                    log.info("[ROTATE] Locked onto challenge at ({}, {}) {}x{} — "
                                    + "both arrows detected job={}",
                            (int) cand.box().x, (int) cand.box().y,
                            (int) cand.box().width, (int) cand.box().height, jobId);
                    break;
                }
                log.info("[ROTATE] Candidate at ({}, {}) {}x{} rejected — "
                                + "both arrows not found (likely decorative) job={}",
                        (int) cand.box().x, (int) cand.box().y,
                        (int) cand.box().width, (int) cand.box().height, jobId);
            }

            if (puzzle == null) {
                log.warn("[ROTATE] No candidate yielded both arrows — abort job={}", jobId);
                return false;
            }

            int totalClicks = runCorrectionLoop(page, puzzle, jobId, arrows);
            clickSubmit(page);
            boolean ok = checkSuccess(page);
            log.info("[ROTATE] Submitted after {} click(s), success={} job={}", totalClicks, ok, jobId);
            return ok;

        } catch (Exception e) {
            log.warn("[ROTATE] Solve failed job={}: {}", jobId, e.getMessage());
            return false;
        }
    }

    private int runCorrectionLoop(Page page, Locator puzzle, UUID jobId, ArrowCoords arrows) {
        CorrectionState state = new CorrectionState();
        for (int round = 0; round <= MAX_CORRECTION_ROUNDS && state.keepGoing; round++) {
            processRound(page, puzzle, jobId, arrows, state, round);
        }
        return state.totalClicks;
    }

    private void processRound(Page page, Locator puzzle, UUID jobId,
                              ArrowCoords arrows, CorrectionState state, int round) {
        RotationProbe probe = probePuzzle(puzzle, jobId);
        if (round > 0 && !puzzleChangedSignificantly(state.prevPuzzleShot, probe.screenshotBytes())) {
            log.warn("[ROTATE] Puzzle unchanged after clicks — clicks are not landing, abort job={}", jobId);
            state.keepGoing = false;
            return;
        }
        state.prevPuzzleShot = probe.screenshotBytes();
        Integer rotation = probe.rotationDegrees();
        if (rotation == null) {
            log.warn("[ROTATE] No rotation consensus on round {} job={}", round, jobId);
            state.keepGoing = false;
        } else if (Math.abs(rotation) <= UPRIGHT_TOLERANCE_DEG) {
            log.info("[ROTATE] Within tolerance ({}deg) after {} click(s) job={}", rotation, state.totalClicks, jobId);
            state.keepGoing = false;
        } else {
            state.rotationHistory.add(rotation);
            if (Math.abs(rotation) > FINE_REFINE_THRESHOLD_DEG
                    && isStuck(state.rotationHistory, rotation, jobId)) {
                state.keepGoing = false;
            } else {
                applyAndUpdateState(page, rotation, jobId, arrows, state);
            }
        }
    }

    private void applyAndUpdateState(Page page, int rotation, UUID jobId,
                                     ArrowCoords arrows, CorrectionState state) {
        int sign = Integer.signum(rotation);

        if (Math.abs(rotation) <= FINE_REFINE_THRESHOLD_DEG) {
            if (state.prevSign != 0 && sign != state.prevSign) {
                log.info("[ROTATE] Oscillation near upright ({}°) — accepting best effort job={}", rotation, jobId);
                state.keepGoing = false;
                return;
            }
            state.prevSign = sign;
            int budget = Math.min(1, MAX_CLICKS_GUARD - state.totalClicks);
            int clicked = applyRotation(page, sign * stepDegrees, jobId, budget, arrows);
            state.totalClicks += clicked;
            state.keepGoing = clicked > 0 && state.totalClicks < MAX_CLICKS_GUARD;
            return;
        }

        state.prevSign = sign;
        int clicked = applyRotation(page, rotation, jobId, MAX_CLICKS_GUARD - state.totalClicks, arrows);
        state.totalClicks += clicked;
        state.keepGoing = clicked > 0 && state.totalClicks < MAX_CLICKS_GUARD;
    }

    private static class CorrectionState {
        int totalClicks = 0;
        int prevSign = 0;
        byte[] prevPuzzleShot = null;
        final List<Integer> rotationHistory = new ArrayList<>();
        boolean keepGoing = true;
    }

    private boolean isStuck(List<Integer> rotationHistory, int rotation, UUID jobId) {
        if (rotationHistory.size() < STUCK_LOOP_THRESHOLD) {
            return false;
        }
        int n = rotationHistory.size();
        int last = rotationHistory.get(n - 1);
        for (int k = 2; k <= STUCK_LOOP_THRESHOLD; k++) {
            if (!rotationHistory.get(n - k).equals(last)) {
                return false;
            }
        }
        log.warn("[ROTATE] LLM stuck on rotation={}deg for {} consecutive rounds — "
                        + "vision model is hallucinating, accept best effort job={}",
                rotation, STUCK_LOOP_THRESHOLD, jobId);
        return true;
    }

    private List<Candidate> locatePuzzleCandidates(Page page, UUID jobId) {
        List<Candidate> ranked = new ArrayList<>();
        try {
            waitForFirstCandidate(page);
            Locator candidates = page.locator(PUZZLE_IMAGE_SELECTOR);
            int count = candidates.count();
            log.info("[ROTATE] Found {} puzzle candidate(s)", count);
            collectCandidates(candidates, count, ranked);
            ranked.sort(Comparator.comparingDouble(Candidate::area).reversed());
        } catch (Exception e) {
            log.warn("[ROTATE] Locate puzzle candidates failed job={}: {}", jobId, e.getMessage());
        }
        return ranked;
    }

    private void waitForFirstCandidate(Page page) {
        page.locator(PUZZLE_IMAGE_SELECTOR)
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(PUZZLE_PROBE_TIMEOUT_MS));
    }

    private void collectCandidates(Locator candidates, int count, List<Candidate> ranked) {
        for (int i = 0; i < count; i++) {
            collectCandidate(candidates.nth(i), i, ranked);
        }
    }

    private void collectCandidate(Locator locator, int index, List<Candidate> ranked) {
        try {
            processCandidate(locator, index, ranked);
        } catch (Exception e) {
            log.debug("[ROTATE] Skipping candidate {}: {}", index, e.getMessage());
        }
    }

    private void processCandidate(Locator c, int i, List<Candidate> ranked) {
        if (!c.isVisible()) return;
        BoundingBox box = c.boundingBox();
        if (box == null) return;
        if (box.width < MIN_PUZZLE_SIZE_PX || box.height < MIN_PUZZLE_SIZE_PX) {
            log.debug("[ROTATE] Skipping small candidate {}x{}", (int) box.width, (int) box.height);
            return;
        }
        double aspect = box.width / box.height;
        if (aspect < 0.6 || aspect > 1.7) {
            log.debug("[ROTATE] Skipping non-square candidate (aspect={})", aspect);
            return;
        }
        double area = box.width * box.height;
        log.info("[ROTATE] Candidate #{}: {}x{} at ({}, {}) area={} | {}",
                i, (int) box.width, (int) box.height,
                (int) box.x, (int) box.y, (int) area, describe(c));
        ranked.add(new Candidate(c, box, area));
    }

    private String describe(Locator c) {
        try {
            Object res = c.evaluate(
                    "el => el.tagName"
                            + " + ' src=' + ((el.getAttribute && el.getAttribute('src')) || '').slice(0, 50)"
                            + " + ' parent=' + (el.parentElement ? (el.parentElement.className || el.parentElement.tagName) : '')");
            return res == null ? "?" : res.toString();
        } catch (Exception e) {
            return "?";
        }
    }

    private ArrowCoords findArrowsViaOpenCV(Page page, BoundingBox puzzleBox, UUID jobId) {
        if (!opencvReady) {
            log.warn("[ROTATE] OpenCV not available — cannot detect arrows job={}", jobId);
            return null;
        }
        try {
            double clipX = Math.max(0, puzzleBox.x - 240);
            double clipY = Math.max(0, puzzleBox.y - 20);
            double clipW = (puzzleBox.x - clipX) + puzzleBox.width + 240;
            double clipH = puzzleBox.height + 40;

            byte[] shot = page.screenshot(new Page.ScreenshotOptions().setClip(clipX, clipY, clipW, clipH));
            if (shot == null || shot.length == 0) return null;

            dumpArrowRegionDebug(shot, puzzleBox, jobId);

            double pRelCx = (puzzleBox.x - clipX) + puzzleBox.width / 2.0;
            double pRelCy = (puzzleBox.y - clipY) + puzzleBox.height / 2.0;
            double puzzleHalfW = puzzleBox.width / 2.0;

            List<MatOfPoint> contours = buildMaskedContours(shot, pRelCx, pRelCy, puzzleHalfW);
            dumpContoursDebug(shot, contours, puzzleBox, jobId);
            return selectBestArrows(contours, clipX, clipY, pRelCx, pRelCy, puzzleHalfW, jobId);

        } catch (Exception t) {
            log.warn("[ROTATE] OpenCV arrow detection failed: {}", t.getMessage());
            return null;
        }
    }

    private List<MatOfPoint> buildMaskedContours(byte[] shot, double pRelCx, double pRelCy, double puzzleHalfW) {
        Mat src = Imgcodecs.imdecode(new MatOfByte(shot), Imgcodecs.IMREAD_COLOR);
        if (src.empty()) return new ArrayList<>();

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Mat binary = new Mat();
        Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
        Imgproc.circle(binary, new Point(pRelCx, pRelCy), (int) (puzzleHalfW + 4), new Scalar(0), -1);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(binary, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        src.release();
        gray.release();
        binary.release();
        return contours;
    }

    private ArrowCoords selectBestArrows(List<MatOfPoint> contours, double clipX, double clipY,
                                         double pRelCx, double pRelCy, double puzzleHalfW, UUID jobId) {
        Rect[] best = findBestArrowRects(contours, pRelCx, pRelCy, puzzleHalfW);
        Rect bestLeft = best[0];
        Rect bestRight = best[1];

        if (bestLeft == null || bestRight == null) {
            log.warn("[ROTATE] OpenCV could not locate both arrows (L={} R={}) job={}",
                    bestLeft, bestRight, jobId);
            return null;
        }
        return buildArrowCoords(bestLeft, bestRight, clipX, clipY, jobId);
    }

    private Rect[] findBestArrowRects(List<MatOfPoint> contours,
                                      double pRelCx, double pRelCy, double puzzleHalfW) {
        Rect bestLeft = null;
        Rect bestRight = null;
        double bestLeftDist = Double.MAX_VALUE;
        double bestRightDist = Double.MAX_VALUE;

        for (MatOfPoint c : contours) {
            Rect r = Imgproc.boundingRect(c);
            double cx = r.x + r.width / 2.0;
            double cy = r.y + r.height / 2.0;
            if (isValidArrowContour(r, cx, cy, pRelCx, pRelCy, puzzleHalfW)) {
                if (cx < pRelCx - puzzleHalfW) {
                    double d = pRelCx - cx;
                    if (d < bestLeftDist) {
                        bestLeftDist = d;
                        bestLeft = r;
                    }
                } else {
                    double d = cx - pRelCx;
                    if (d < bestRightDist) {
                        bestRightDist = d;
                        bestRight = r;
                    }
                }
            }
        }
        return new Rect[]{bestLeft, bestRight};
    }

    private boolean isValidArrowContour(Rect r, double cx, double cy,
                                        double pRelCx, double pRelCy, double puzzleHalfW) {
        return r.width >= 6 && r.width <= 90
                && r.height >= 6 && r.height <= 90
                && Math.abs(cy - pRelCy) <= 60
                && (cx < pRelCx - puzzleHalfW || cx > pRelCx + puzzleHalfW);
    }

    private ArrowCoords buildArrowCoords(Rect bestLeft, Rect bestRight,
                                         double clipX, double clipY, UUID jobId) {
        double absLx = clipX + bestLeft.x + bestLeft.width / 2.0;
        double absLy = clipY + bestLeft.y + bestLeft.height / 2.0;
        double absRx = clipX + bestRight.x + bestRight.width / 2.0;
        double absRy = clipY + bestRight.y + bestRight.height / 2.0;

        log.info("[ROTATE] OpenCV arrows: LEFT=({}, {}) [{}x{}] RIGHT=({}, {}) [{}x{}] job={}",
                (int) absLx, (int) absLy, bestLeft.width, bestLeft.height,
                (int) absRx, (int) absRy, bestRight.width, bestRight.height, jobId);

        return new ArrowCoords(absLx, absLy, absRx, absRy);
    }

    private void dumpArrowRegionDebug(byte[] shot, BoundingBox puzzleBox, UUID jobId) {
        if (!debugDump) return;
        try {
            Path dir = Files.createDirectories(Path.of(TARGET_DIR, CAPTCHA_DEBUG_DIR));
            Files.write(dir.resolve("rotate-arrows-region-" + jobId + "-"
                    + (int) puzzleBox.x + "x" + (int) puzzleBox.y + ".png"), shot);
        } catch (Exception e) {
            log.debug("[ROTATE] Arrow region debug dump failed: {}", e.getMessage());
        }
    }

    private void dumpContoursDebug(byte[] shot, List<MatOfPoint> contours, BoundingBox puzzleBox, UUID jobId) {
        if (!debugDump) return;
        try {
            Mat src = Imgcodecs.imdecode(new MatOfByte(shot), Imgcodecs.IMREAD_COLOR);
            Mat dbg = src.clone();
            for (MatOfPoint c : contours) {
                Imgproc.rectangle(dbg, Imgproc.boundingRect(c), new Scalar(0, 0, 255), 1);
            }
            MatOfByte buf = new MatOfByte();
            Imgcodecs.imencode(".png", dbg, buf);
            Path dir = Files.createDirectories(Path.of(TARGET_DIR, CAPTCHA_DEBUG_DIR));
            Files.write(dir.resolve("rotate-contours-" + jobId + "-"
                    + (int) puzzleBox.x + "x" + (int) puzzleBox.y + ".png"), buf.toArray());
            dbg.release();
            src.release();
        } catch (Exception e) {
            log.debug("[ROTATE] Contours debug dump failed: {}", e.getMessage());
        }
    }

    private boolean puzzleChangedSignificantly(byte[] before, byte[] after) {
        if (before == null || after == null) return true;
        if (!opencvReady) return true;
        try {
            Mat m1 = Imgcodecs.imdecode(new MatOfByte(before), Imgcodecs.IMREAD_GRAYSCALE);
            Mat m2 = Imgcodecs.imdecode(new MatOfByte(after), Imgcodecs.IMREAD_GRAYSCALE);
            if (m1.empty() || m2.empty() || !m1.size().equals(m2.size())) {
                if (!m1.empty()) m1.release();
                if (!m2.empty()) m2.release();
                return true;
            }
            Mat diff = new Mat();
            Core.absdiff(m1, m2, diff);
            double meanDiff = Core.mean(diff).val[0];
            m1.release();
            m2.release();
            diff.release();
            log.debug("[ROTATE] Puzzle diff mean={}", meanDiff);
            return meanDiff > PUZZLE_CHANGE_THRESHOLD;
        } catch (Throwable t) {
            return true;
        }
    }

    private RotationProbe probePuzzle(Locator puzzle, UUID jobId) {
        byte[] shot = screenshot(puzzle, jobId);
        if (shot.length == 0) return new RotationProbe(null, null);
        dumpPuzzleScreenshot(shot, jobId);
        byte[] enhanced = opencvReady ? enhanceForLlm(shot, jobId) : shot;

        String b64 = Base64.getEncoder().encodeToString(enhanced);
        Integer rotation = askLlmForRotationWithVoting(b64, jobId);
        return new RotationProbe(shot, rotation);
    }

    private byte[] enhanceForLlm(byte[] png, UUID jobId) {
        Mat src = Imgcodecs.imdecode(new MatOfByte(png), Imgcodecs.IMREAD_COLOR);
        if (src.empty()) return png;
        Mat big = new Mat();
        Mat lab = new Mat();
        Mat out = new Mat();
        List<Mat> channels = new ArrayList<>();
        try {
            Imgproc.resize(src, big, new Size(LLM_UPSCALE_PX, LLM_UPSCALE_PX), 0, 0, Imgproc.INTER_CUBIC);
            Imgproc.cvtColor(big, lab, Imgproc.COLOR_BGR2Lab);
            Core.split(lab, channels);
            CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(channels.getFirst(), channels.getFirst());
            Core.merge(channels, lab);
            Imgproc.cvtColor(lab, out, Imgproc.COLOR_Lab2BGR);

            MatOfByte buf = new MatOfByte();
            Imgcodecs.imencode(".png", out, buf);
            byte[] result = buf.toArray();
            buf.release();
            if (debugDump) dumpEnhancedDebug(result, jobId);
            return result.length > 0 ? result : png;
        } catch (Exception e) {
            log.warn("[ROTATE] LLM image enhancement failed, using raw shot: {}", e.getMessage());
            return png;
        } finally {
            src.release();
            big.release();
            lab.release();
            out.release();
            channels.forEach(Mat::release);
        }
    }

    private void dumpEnhancedDebug(byte[] enhanced, UUID jobId) {
        try {
            Path dir = Files.createDirectories(Path.of(TARGET_DIR, CAPTCHA_DEBUG_DIR));
            Files.write(dir.resolve("rotate-enhanced-" + jobId + "-" + System.currentTimeMillis() + ".png"), enhanced);
        } catch (Exception e) {
            log.debug("[ROTATE] Enhanced debug dump failed: {}", e.getMessage());
        }
    }

    private int applyRotation(Page page, int rotation, UUID jobId, int maxClicks, ArrowCoords arrows) {
        boolean useRightArrow = rotation < 0;
        int absDeg = Math.abs(rotation);

        int forwardClicks = (int) Math.round(absDeg / (double) stepDegrees);
        int reverseClicks = (int) Math.round((360 - absDeg) / (double) stepDegrees);
        int clicks;
        if (reverseClicks < forwardClicks) {
            clicks = reverseClicks;
            useRightArrow = !useRightArrow;
        } else {
            clicks = forwardClicks;
        }

        int safeMax = Math.max(0, maxClicks);
        clicks = Math.clamp(clicks, 0, safeMax);
        if (clicks == 0) {
            log.info("[ROTATE] No clicks needed for residual {}deg (step={}deg) job={}",
                    rotation, stepDegrees, jobId);
            return 0;
        }

        double targetX = useRightArrow ? arrows.rightX() : arrows.leftX();
        double targetY = useRightArrow ? arrows.rightY() : arrows.leftY();

        log.info("[ROTATE] rotation={}deg -> {} click(s) {} at ({}, {}) job={}",
                rotation, clicks, useRightArrow ? "RIGHT" : "LEFT",
                (int) targetX, (int) targetY, jobId);

        int done = 0;
        for (int i = 0; i < clicks; i++) {
            try {
                support.clickHumanlikeAt(page, targetX, targetY);
                done++;
                sleep(CLICK_DELAY_MIN_MS + (long) rnd(CLICK_DELAY_JITTER_MS));
            } catch (Exception e) {
                log.warn("[ROTATE] Arrow click {} failed: {}", i + 1, e.getMessage());
            }
        }
        sleep(POST_ROTATE_WAIT_MS);
        return done;
    }

    private void clickSubmit(Page page) {
        try {
            Locator submit = page.locator(SUBMIT_SELECTOR).first();
            support.clickHumanlike(page, submit);
        } catch (Exception e) {
            log.warn("[ROTATE] Submit button click failed: {}", e.getMessage());
        }
    }

    private boolean checkSuccess(Page page) {
        sleep(POST_SUBMIT_WAIT_MS);
        try {
            Locator success = page.locator(SUCCESS_SELECTOR).first();
            if (success.isVisible(new Locator.IsVisibleOptions().setTimeout(SUCCESS_PROBE_TIMEOUT_MS))) {
                return true;
            }
        } catch (Exception e) {
            log.debug("[ROTATE] Success marker not found: {}", e.getMessage());
        }
        try {
            Locator puzzle = page.locator(PUZZLE_IMAGE_SELECTOR).first();
            return !puzzle.isVisible(new Locator.IsVisibleOptions().setTimeout(1_500));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] screenshot(Locator puzzle, UUID jobId) {
        try {
            byte[] img = puzzle.screenshot();
            if (img == null || img.length == 0) {
                log.warn("[ROTATE] Empty puzzle screenshot job={}", jobId);
                return new byte[0];
            }
            return img;
        } catch (Exception e) {
            log.warn("[ROTATE] Screenshot failed job={}: {}", jobId, e.getMessage());
            return new byte[0];
        }
    }

    private void dumpPuzzleScreenshot(byte[] shot, UUID jobId) {
        if (!debugDump) return;
        try {
            Path dir = Files.createDirectories(Path.of(TARGET_DIR, CAPTCHA_DEBUG_DIR));
            Path file = dir.resolve("rotate-" + jobId + "-" + System.currentTimeMillis() + ".png");
            Files.write(file, shot);
            log.info("[ROTATE] Debug puzzle screenshot dumped -> {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[ROTATE] Debug dump failed: {}", e.getMessage());
        }
    }

    private Integer askLlmForRotationWithVoting(String b64, UUID jobId) {
        List<Integer> rotations = new ArrayList<>();
        for (int i = 0; i < VOTING_PASSES; i++) {
            Integer r = askLlmForRotationOnce(b64);
            log.debug("[ROTATE] Pass {}/{} rotation={} job={}", i + 1, VOTING_PASSES, r, jobId);
            if (r != null) rotations.add(r);
        }
        if (rotations.isEmpty()) return null;
        double sumSin = 0;
        double sumCos = 0;
        for (int r : rotations) {
            double rad = Math.toRadians(r);
            sumSin += Math.sin(rad);
            sumCos += Math.cos(rad);
        }
        int n = rotations.size();
        double meanRad = Math.atan2(sumSin / n, sumCos / n);
        double concentration = Math.hypot(sumSin / n, sumCos / n);
        int consensus = normalize((int) Math.round(Math.toDegrees(meanRad)));

        log.info("[ROTATE] votes={} circular-mean={}deg concentration={} job={}",
                rotations, consensus, String.format("%.2f", concentration), jobId);

        if (concentration < MIN_VOTE_CONCENTRATION) {
            log.warn("[ROTATE] Votes too spread (R={} < {}) — LLM unreliable, skip job={}",
                    String.format("%.2f", concentration), MIN_VOTE_CONCENTRATION, jobId);
            return null;
        }

        return invertSign ? normalize(-consensus) : consensus;
    }

    private Integer askLlmForRotationOnce(String b64) {
        try {
            return doRotationLlmCall(b64);
        } catch (LlmRateLimitException rate) {
            log.warn("[ROTATE] Groq rate limit — backing off {}ms", rate.getRetryAfter().toMillis());
            sleep(rate.getRetryAfter().toMillis());
            try {
                return doRotationLlmCall(b64);
            } catch (Exception retryFailed) {
                log.warn("[ROTATE] LLM still failed after backoff: {}", retryFailed.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.warn("[ROTATE] LLM rotation call failed: {}", e.getMessage());
            return null;
        }
    }

    private Integer doRotationLlmCall(String b64) {
        String prompt = """
                You are solving an image-rotation CAPTCHA. The image shows a single
                object or scene that has been rotated around its centre, away from its
                natural upright orientation.

                Your task: estimate the angle by which the image is CURRENTLY rotated,
                so that rotating it by the opposite amount restores the upright view.

                What "upright" means:
                  - Animals / people: head and back up, legs/feet down, standing naturally.
                  - Objects: their natural top up (antlers/crown/cap up, base down).
                  - Scenes: sky/ceiling up, ground down.

                Convention for rotation_degrees (signed integer, -180..180):
                  -   0  -> already upright
                  - +90  -> the image is rotated 90° CLOCKWISE from upright
                  - -90  -> the image is rotated 90° COUNTER-CLOCKWISE from upright
                  - +180 / -180 -> upside down

                Be precise: give the ACTUAL tilt, not only multiples of 90.
                Estimates like -47, 128, 15, -160 are expected and encouraged.

                Steps:
                  1. Identify the object and where its natural top currently points.
                  2. Estimate how many degrees, and in which direction, it is rotated.

                Respond with ONLY a JSON object (no markdown, no prose):
                {
                  "object": "<what it is>",
                  "rotation_degrees": <signed integer -180..180>,
                  "confidence": <0.0-1.0>
                }
                """;

        String raw = visionClient.analyzeImage(prompt, b64, MIME_PNG);
        List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
        if (parsed.isEmpty()) {
            log.warn("[ROTATE] LLM returned empty parse result");
            return null;
        }

        Map<String, Object> first = parsed.getFirst();
        Object degObj = first.get("rotation_degrees");

        log.info("[ROTATE] LLM object='{}' rotation_degrees={} confidence={}",
                first.get("object"), degObj, first.get("confidence"));

        if (degObj instanceof Number n) {
            return normalize(n.intValue());
        }

        Object dirObj = first.get("top_direction");
        if (dirObj != null) {
            return directionToRotation(dirObj.toString().trim().toLowerCase());
        }
        return null;
    }

    private static Integer directionToRotation(String direction) {
        if (direction == null) return null;
        return switch (direction) {
            case "up", "upright", "top" -> 0;
            case "right" -> 90;
            case "down", "bottom", "upside-down", "upsidedown" -> 180;
            case "left" -> -90;
            default -> {
                log.warn("[ROTATE] Unknown top_direction='{}' from LLM — treating as null", direction);
                yield null;
            }
        };
    }

    private static int normalize(int deg) {
        while (deg > 180) deg -= 360;
        while (deg <= -180) deg += 360;
        return deg;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int rnd(int bound) {
        return bound <= 0 ? 0 : ThreadLocalRandom.current().nextInt(0, bound);
    }
}