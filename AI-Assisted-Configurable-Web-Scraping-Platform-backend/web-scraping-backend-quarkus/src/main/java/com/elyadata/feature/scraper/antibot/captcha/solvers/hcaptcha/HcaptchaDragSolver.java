package com.elyadata.feature.scraper.antibot.captcha.solvers.hcaptcha;

import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.solvers.llm.HcaptchaPromptFactory;
import com.elyadata.feature.scraper.browser.humanizer.motion.BezierMouseMotion;
import com.microsoft.playwright.FrameLocator;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HcaptchaDragSolver {

    private static final String ELEMENT_SELECTOR =
            ".task-image, .answer-image, .task__image, .image-task, .task, .answer, " +
                    "[class*='piece'], [role='button'][aria-label], [aria-label*='task']";

    private static final String[] PATTERN_SELECTORS = {
            "canvas",
            ".challenge-image",
            ".challenge .image",
            ".challenge-container img"
    };

    private static final int MIN_ELEMENT_SIZE = 30;
    private static final int VOTING_PASSES = 3;
    private static final int DRAG_STEPS_MIN = 38;
    private static final int DRAG_STEPS_MAX = 62;
    private static final int PRE_GRAB_PAUSE_MS = 280;
    private static final int POST_DROP_PAUSE_MS = 800;

    private final VisionLlmClient visionClient;
    private final BezierMouseMotion mouseMotion;
    private final HcaptchaPromptFactory prompts;
    private final HcaptchaSlotAnalyzer slotAnalyzer;

    public boolean solve(Page page, FrameLocator challenge, String instruction, UUID jobId) {
        try {
            log.info("[HCAPTCHA-DRAG] Starting drag-puzzle solve (CV ready={}) job={}",
                    slotAnalyzer.isReady(), jobId);

            Locator allElements = challenge.locator(ELEMENT_SELECTOR);
            int total = allElements.count();
            log.info("[HCAPTCHA-DRAG] Found {} visual elements job={}", total, jobId);

            if (total < 2) {
                dumpDomDiagnostic(challenge, jobId);
                log.warn("[HCAPTCHA-DRAG] Not enough visual elements ({}) job={}", total, jobId);
                return false;
            }

            List<IndexedBox> sized = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                try {
                    BoundingBox b = allElements.nth(i).boundingBox();
                    if (b != null && b.width >= MIN_ELEMENT_SIZE && b.height >= MIN_ELEMENT_SIZE) {
                        sized.add(new IndexedBox(i, b));
                    }
                } catch (Exception ignored) {
                }
            }

            if (sized.size() < 2) {
                dumpDomDiagnostic(challenge, jobId);
                log.warn("[HCAPTCHA-DRAG] Too few sized elements ({}) job={}", sized.size(), jobId);
                return false;
            }

            PatternResolution pattern = resolvePattern(challenge, sized, jobId);
            if (pattern == null) return false;

            List<IndexedBox> pieces = collectPieces(sized, pattern);
            if (pieces.size() < 2) {
                dumpDomDiagnostic(challenge, jobId);
                log.warn("[HCAPTCHA-DRAG] Not enough pieces ({}) job={}", pieces.size(), jobId);
                return false;
            }

            BoundingBox pb = pattern.box();
            log.info("[HCAPTCHA-DRAG] Pattern={}x{} at ({},{}); pieces={} job={}",
                    (int) pb.width, (int) pb.height, (int) pb.x, (int) pb.y,
                    pieces.size(), jobId);

            byte[] patternImg = capturePatternImage(challenge, pb);
            java.util.Optional<HcaptchaSlotAnalyzer.SlotPosition> cvSlot =
                    patternImg != null
                            ? slotAnalyzer.findEmptySlot(patternImg)
                            : java.util.Optional.empty();

            byte[] annotated = screenshotAndAnnotate(challenge, pieces);
            if (annotated == null) return false;
            String b64 = Base64.getEncoder().encodeToString(annotated);

            DragSolution sol = askLlmWithVoting(b64, pieces.size(), instruction, VOTING_PASSES, jobId);
            if (sol == null) {
                log.warn("[HCAPTCHA-DRAG] LLM produced no usable solution job={}", jobId);
                return false;
            }

            int finalSlotX = sol.slotXPct();
            int finalSlotY = sol.slotYPct();
            if (cvSlot.isPresent()) {
                finalSlotX = (int) Math.round(cvSlot.get().xPct());
                finalSlotY = (int) Math.round(cvSlot.get().yPct());
                log.info("[HCAPTCHA-DRAG] OpenCV slot=({}%, {}%) overrides LLM ({}%, {}%) job={}",
                        finalSlotX, finalSlotY, sol.slotXPct(), sol.slotYPct(), jobId);
            } else {
                log.info("[HCAPTCHA-DRAG] Using LLM slot=({}%, {}%) (no CV detection) job={}",
                        finalSlotX, finalSlotY, jobId);
            }
            log.info("[HCAPTCHA-DRAG] Final: piece={} slot=({}%, {}%) job={}",
                    sol.pieceIndex(), finalSlotX, finalSlotY, jobId);

            if (sol.pieceIndex() < 0 || sol.pieceIndex() >= pieces.size()) {
                log.warn("[HCAPTCHA-DRAG] Invalid piece index {} job={}", sol.pieceIndex(), jobId);
                return false;
            }

            BoundingBox pieceBox = pieces.get(sol.pieceIndex()).box();
            double sourceX = pieceBox.x + pieceBox.width / 2.0;
            double sourceY = pieceBox.y + pieceBox.height / 2.0;
            double targetX = pb.x + pb.width * clampPct(finalSlotX) / 100.0;
            double targetY = pb.y + pb.height * clampPct(finalSlotY) / 100.0;

            performDrag(page, sourceX, sourceY, targetX, targetY, jobId);
            return true;

        } catch (Exception e) {
            log.warn("[HCAPTCHA-DRAG] Solve threw job={}: {}", jobId, e.getMessage());
            return false;
        }
    }

    private byte[] capturePatternImage(FrameLocator challenge, BoundingBox patternBox) {
        try {
            Locator canvas = challenge.locator("canvas").first();
            if (canvas.isVisible(new Locator.IsVisibleOptions().setTimeout(800))) {
                return canvas.screenshot();
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            byte[] body = challenge.locator("body").first().screenshot();
            BufferedImage full = ImageIO.read(new ByteArrayInputStream(body));
            if (full == null) return new byte[0];
            BoundingBox bodyBox = challenge.locator("body").first().boundingBox();
            if (bodyBox == null) return new byte[0];
            int x = Math.max(0, (int) (patternBox.x - bodyBox.x));
            int y = Math.max(0, (int) (patternBox.y - bodyBox.y));
            int w = Math.min((int) patternBox.width, full.getWidth() - x);
            int h = Math.min((int) patternBox.height, full.getHeight() - y);
            if (w <= 0 || h <= 0) return new byte[0];
            BufferedImage cropped = full.getSubimage(x, y, w, h);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("[HCAPTCHA-DRAG] capturePatternImage failed: {}", e.getMessage());
            return null;
        }
    }

    private PatternResolution resolvePattern(FrameLocator challenge,
                                             List<IndexedBox> sized,
                                             UUID jobId) {
        for (String sel : PATTERN_SELECTORS) {
            try {
                Locator loc = challenge.locator(sel).first();
                if (loc.isVisible(new Locator.IsVisibleOptions().setTimeout(1_200))) {
                    BoundingBox b = loc.boundingBox();
                    if (b != null && b.width > 50 && b.height > 50) {
                        log.info("[HCAPTCHA-DRAG] Pattern via selector '{}' job={}", sel, jobId);
                        return new PatternResolution(b, -1);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        IndexedBox biggest = sized.stream()
                .max(Comparator.comparingDouble(b -> b.box().width * b.box().height))
                .orElse(null);
        if (biggest == null) {
            log.warn("[HCAPTCHA-DRAG] No pattern candidate found job={}", jobId);
            return null;
        }
        log.info("[HCAPTCHA-DRAG] Pattern via biggest-element fallback: domIdx={} ({}x{}) job={}",
                biggest.index(), (int) biggest.box().width, (int) biggest.box().height, jobId);
        return new PatternResolution(biggest.box(), biggest.index());
    }

    private List<IndexedBox> collectPieces(List<IndexedBox> sized, PatternResolution pattern) {
        List<IndexedBox> pieces = new ArrayList<>();
        BoundingBox pb = pattern.box();
        for (IndexedBox b : sized) {
            if (b.index() == pattern.excludeIndex()) continue;
            BoundingBox bb = b.box();
            double cx = bb.x + bb.width / 2.0;
            double cy = bb.y + bb.height / 2.0;
            boolean inside = cx >= pb.x && cx <= pb.x + pb.width
                    && cy >= pb.y && cy <= pb.y + pb.height;
            if (inside) continue;
            pieces.add(b);
        }
        pieces.sort(Comparator.comparingDouble(b -> b.box().x));
        return pieces;
    }

    private byte[] screenshotAndAnnotate(FrameLocator challenge,
                                         List<IndexedBox> pieces) {
        try {
            byte[] raw = challenge.locator("body").first().screenshot();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) return new byte[0];

            BoundingBox bodyBox = challenge.locator("body").first().boundingBox();
            double offsetX = bodyBox == null ? 0 : bodyBox.x;
            double offsetY = bodyBox == null ? 0 : bodyBox.y;

            Graphics2D g = img.createGraphics();
            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            for (int i = 0; i < pieces.size(); i++) {
                BoundingBox b = pieces.get(i).box();
                int x = (int) (b.x - offsetX) + 4;
                int y = (int) (b.y - offsetY) + 4;
                g.setColor(Color.RED);
                g.fillRect(x, y, 28, 24);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(i), x + 8, y + 19);
            }
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("[HCAPTCHA-DRAG] screenshotAndAnnotate failed: {}", e.getMessage());
            return null;
        }
    }

    private DragSolution askLlmWithVoting(String b64, int pieceCount, String instruction,
                                          int passes, UUID jobId) {
        Map<Integer, Integer> pieceVotes = new HashMap<>();
        List<Integer> slotXs = new ArrayList<>();
        List<Integer> slotYs = new ArrayList<>();
        double bestConf = 0;

        for (int i = 0; i < passes; i++) {
            DragSolution s = askLlm(b64, pieceCount, instruction);
            if (s == null) continue;
            log.info("[HCAPTCHA-DRAG-VOTE] Pass {}/{}: piece={} slot=({}%, {}%) conf={} job={}",
                    i + 1, passes, s.pieceIndex(), s.slotXPct(), s.slotYPct(),
                    s.confidence(), jobId);
            pieceVotes.merge(s.pieceIndex(), 1, Integer::sum);
            slotXs.add(s.slotXPct());
            slotYs.add(s.slotYPct());
            bestConf = Math.max(bestConf, s.confidence());
        }

        if (pieceVotes.isEmpty()) return null;

        int piece = pieceVotes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElseThrow();
        Collections.sort(slotXs);
        Collections.sort(slotYs);
        int slotX = slotXs.get(slotXs.size() / 2);
        int slotY = slotYs.get(slotYs.size() / 2);

        log.info("[HCAPTCHA-DRAG-VOTE] Consensus piece={} (votes={}) slot=({}%, {}%) job={}",
                piece, pieceVotes, slotX, slotY, jobId);

        return new DragSolution(piece, slotX, slotY, bestConf);
    }

    private DragSolution askLlm(String b64, int pieceCount, String instruction) {
        try {
            String raw = visionClient.analyzeImage(
                    prompts.dragPuzzlePrompt(pieceCount, instruction), b64, "image/png");
            log.debug("[HCAPTCHA-DRAG-LLM] raw: {}",
                    raw == null ? "null" : raw.substring(0, Math.min(300, raw.length())));

            List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
            if (parsed.isEmpty()) return null;

            Map<String, Object> obj = parsed.getFirst();
            int idx = toInt(obj.get("piece_index"));
            int sx = toInt(obj.get("slot_x_pct"));
            int sy = toInt(obj.get("slot_y_pct"));
            double conf = toDouble(obj.get("confidence"));
            if (idx < 0 || sx < 0 || sy < 0) return null;
            return new DragSolution(idx, sx, sy, conf);
        } catch (Exception e) {
            log.warn("[HCAPTCHA-DRAG-LLM] call failed: {}", e.getMessage());
            return null;
        }
    }

    private void performDrag(Page page, double sx, double sy, double tx, double ty, UUID jobId) {
        log.info("[HCAPTCHA-DRAG] Drag ({}, {}) → ({}, {}) job={}",
                (int) sx, (int) sy, (int) tx, (int) ty, jobId);

        mouseMotion.moveTo(page, sx, sy, 24);
        sleepMs(PRE_GRAB_PAUSE_MS + ThreadLocalRandom.current().nextInt(0, 200));

        Mouse mouse = page.mouse();
        mouse.down();
        sleepMs(140 + ThreadLocalRandom.current().nextInt(0, 160));

        int totalSteps = ThreadLocalRandom.current().nextInt(DRAG_STEPS_MIN, DRAG_STEPS_MAX);
        for (int i = 1; i <= totalSteps; i++) {
            double t = (double) i / totalSteps;
            double progress = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            double curX = sx + (tx - sx) * progress;
            double curY = sy + (ty - sy) * progress
                    + Math.sin(i * 0.35) * 1.8
                    + ThreadLocalRandom.current().nextDouble(-0.6, 0.6);
            mouse.move(curX, curY, new Mouse.MoveOptions().setSteps(1));
            int microDelay = i < totalSteps * 0.25
                    ? 9 + ThreadLocalRandom.current().nextInt(0, 8)
                    : i < totalSteps * 0.75
                    ? 14 + ThreadLocalRandom.current().nextInt(0, 8)
                    : 22 + ThreadLocalRandom.current().nextInt(0, 14);
            sleepMs(microDelay);
        }

        sleepMs(180 + ThreadLocalRandom.current().nextInt(0, 220));
        mouse.up();
        sleepMs(POST_DROP_PAUSE_MS);
    }

    private void dumpDomDiagnostic(FrameLocator challenge, UUID jobId) {
        try {
            Object counts = challenge.locator("body").first().evaluate("""
                    (body) => {
                      const candidates = [
                        '.task-image', '.answer-image', '.task__image', '.image-task',
                        '.task', '.answer', '.tile', '[class*="piece"]',
                        'canvas', '.challenge-image', '.challenge img',
                        '[role="button"]', '[aria-label]', 'div[tabindex]',
                        'img', '[draggable="true"]', '[draggable]'
                      ];
                      const result = {};
                      for (const sel of candidates) {
                        try { result[sel] = body.querySelectorAll(sel).length; }
                        catch(e) { result[sel] = 'err'; }
                      }
                      return result;
                    }
                    """);
            log.warn("[HCAPTCHA-DRAG-DIAG] Selector counts: {} job={}", counts, jobId);
        } catch (Exception e) {
            log.warn("[HCAPTCHA-DRAG-DIAG] count probe failed: {}", e.getMessage());
        }
    }

    private static int clampPct(int v) {
        return Math.max(5, Math.min(95, v));
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return -1;
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
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

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record IndexedBox(int index, BoundingBox box) {
    }

    private record PatternResolution(BoundingBox box, int excludeIndex) {
    }

    private record DragSolution(int pieceIndex, int slotXPct, int slotYPct, double confidence) {
    }
}