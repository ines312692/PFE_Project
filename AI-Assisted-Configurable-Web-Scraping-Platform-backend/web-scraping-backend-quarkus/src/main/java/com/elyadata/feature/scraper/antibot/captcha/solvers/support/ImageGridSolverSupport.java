package com.elyadata.feature.scraper.antibot.captcha.solvers.support;

import com.elyadata.common.llm.LlmRateLimitException;
import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.VisionLlmClient;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ImageGridSolverSupport {
    public record ClickPoint(double xPct, double yPct, String label) {
    }

    private static final int MOVE_STEPS_MIN = 18;
    private static final int MOVE_STEPS_MAX = 40;
    private static final int POST_CLICK_WAIT_MS = 700;
    private static final int VISIBILITY_TIMEOUT_MS = 10_000;
    private static final int VOTING_PASSES = 3;

    private static final double CLICK_CLUSTER_THRESHOLD = 0.08;
    private static final int CLICK_MIN_VOTES = 2;
    private static final String IMAGE_PNG = "image/png";
    private static final String KEY_REASONING = "reasoning";
    private static final String KEY_CONFIDENCE = "confidence";
    private static final String DIR_TARGET = "target";
    private static final String DIR_CAPTCHA_DEBUG = "captcha-debug";

    private final VisionLlmClient visionClient;

    @ConfigProperty(name = "captcha.debug.dump-grids", defaultValue = "false")
    boolean dumpGrids;


    public void clickHumanlike(Page page, Locator locator) {
        try {
            locator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(VISIBILITY_TIMEOUT_MS));
            locator.scrollIntoViewIfNeeded();

            BoundingBox box = locator.boundingBox();
            if (box == null) {
                log.warn("[IMG-GRID] No bounding box — falling back to direct click");
                locator.click();
                return;
            }

            double jitterX = ThreadLocalRandom.current().nextDouble(-3, 3);
            double jitterY = ThreadLocalRandom.current().nextDouble(-3, 3);
            double targetX = box.x + box.width / 2.0 + jitterX;
            double targetY = box.y + box.height / 2.0 + jitterY;

            int steps = ThreadLocalRandom.current().nextInt(MOVE_STEPS_MIN, MOVE_STEPS_MAX);

            log.debug("[IMG-GRID] Moving mouse to ({}, {}) in {} steps", targetX, targetY, steps);

            page.mouse().move(targetX, targetY, new Mouse.MoveOptions().setSteps(steps));
            sleep(humanDelay(80, 220));

            page.mouse().down();
            sleep(humanDelay(40, 130));
            page.mouse().up();

            sleep(POST_CLICK_WAIT_MS);
        } catch (Exception e) {
            log.debug("[IMG-GRID] Humanlike click failed, fallback to direct: {}", e.getMessage());
            try {
                locator.click();
            } catch (Exception ignored) {
                log.warn("[IMG-GRID] Direct click also failed for locator");
            }
        }
    }

    public void clickHumanlikeAt(Page page, double x, double y) {
        try {
            double jitterX = ThreadLocalRandom.current().nextDouble(-3, 3);
            double jitterY = ThreadLocalRandom.current().nextDouble(-3, 3);
            double targetX = x + jitterX;
            double targetY = y + jitterY;

            int steps = ThreadLocalRandom.current().nextInt(MOVE_STEPS_MIN, MOVE_STEPS_MAX);

            log.debug("[IMG-GRID] Coordinate-click → ({}, {}) in {} steps", targetX, targetY, steps);

            page.mouse().move(targetX, targetY, new Mouse.MoveOptions().setSteps(steps));
            sleep(humanDelay(80, 220));

            page.mouse().down();
            sleep(humanDelay(40, 130));
            page.mouse().up();

            sleep(POST_CLICK_WAIT_MS);
        } catch (Exception e) {
            log.warn("[IMG-GRID] clickHumanlikeAt failed at ({}, {}): {}", x, y, e.getMessage());
        }
    }

    public List<Integer> askLlmForTiles(String instruction, String screenshotB64, int gridSize) {
        try {
            return doLlmCall(instruction, screenshotB64, gridSize);
        } catch (LlmRateLimitException rate) {
            log.warn("[IMG-GRID] Groq rate limit — backing off {}ms (provider={})",
                    rate.getRetryAfter().toMillis(), rate.getProvider());
            sleepQuietly(rate.getRetryAfter());
            try {
                return doLlmCall(instruction, screenshotB64, gridSize);
            } catch (Exception retryFailed) {
                log.warn("[IMG-GRID] LLM call still failed after backoff: {}", retryFailed.getMessage());
                return List.of();
            }
        } catch (Exception e) {
            log.warn("[IMG-GRID] LLM tile selection failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Integer> doLlmCall(String instruction, String screenshotB64, int gridSize) {
        int lastIndex = gridSize * gridSize - 1;
        String cleanInstruction = cleanInstruction(instruction);
        String numberedB64 = prepareGridImage(screenshotB64, gridSize);
        String policy = decisionPolicy(gridSize >= 4);

        String prompt = """
                You are solving a Google reCAPTCHA v2 image challenge.
                You are looking at a %dx%d grid of images, indexed 0 to %d (left-to-right, top-to-bottom).
                
                IMPORTANT: Each tile has its index number printed in WHITE on a RED box
                in its top-left corner. Report tiles using ONLY those printed numbers —
                do not count cells yourself.
                
                INSTRUCTION: "%s"
                
                VISUAL DISAMBIGUATION (apply STRICTLY):
                
                "motorcycle / moto":
                  ✓ Two wheels, exposed engine, handlebars, rider sits astride
                  ✓ Includes scooters Vespa-style with step-through frame
                  ✗ Cars (4 wheels, enclosed cabin) — NEVER select
                  ✗ Bicycles (no engine, pedals visible)
                  ✗ ATVs/quads (4 wheels)
                
                "bus / autobus":
                  ✓ Long rectangular vehicle, multiple windows in a row, raised body
                  ✓ Visible signage like "BUS" or route numbers
                  ✗ Trucks (cargo box, no passenger windows)
                  ✗ Vans/minivans (short body, family vehicle proportions)
                  ✗ Trams (rails visible, overhead wires)
                
                "traffic light / feu de circulation":
                  ✓ Vertical or horizontal box with 2-3 colored circular lights (red/yellow/green)
                  ✓ Mounted on a pole, suspended cable, or overhead arm
                  ✗ Street lamps (single white light, often dome-shaped)
                  ✗ Road signs (text or symbols, not circular lights)
                  ✗ Pedestrian signals (walking-man icons, not vehicular)
                
                "crosswalk / passage pieton":
                  ✓ Parallel white stripes painted ON the road surface
                  ✗ Sidewalks (gray/concrete, BESIDE the road)
                  ✗ Lane markings (single lines, not stripe patterns)
                
                "fire hydrant / bouche incendie":
                  ✓ Short metal post on sidewalk, usually red/yellow, capped with valves
                  ✗ Standpipes (taller, on building walls)
                  ✗ Mailboxes, parking meters
                
                "bicycle / velo":
                  ✓ Two wheels, pedals, no motor, lightweight frame
                  ✗ Motorcycles (motor present)
                  ✗ Tricycles
                
                "boat / bateau":
                  ✓ Watercraft on water, hull visible
                  ✗ Jet-skis (unless instruction includes them)
                  ✗ Inflatable rafts, paddleboards
                
                %s
                
                Examples for a 3x3 grid (lastIndex=8):
                - Clear matches: {"tiles": [1, 4], "confidence": 0.9, "reasoning": "two clear targets"}
                - No matches: {"tiles": [], "confidence": 0.95, "reasoning": "no target visible"}
                - Ambiguous: {"tiles": [3], "confidence": 0.6, "reasoning": "only 1 certain match, others ambiguous"}
                
                Respond with ONLY a JSON object (no markdown, no prose):
                {"tiles": [<indices>], "confidence": <0.0-1.0>, "reasoning": "<brief>"}
                """.formatted(gridSize, gridSize, lastIndex, cleanInstruction, policy);

        String raw = visionClient.analyzeImage(prompt, numberedB64, IMAGE_PNG);
        List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
        if (parsed.isEmpty()) {
            log.warn("[IMG-GRID] LLM returned empty parse result");
            return List.of();
        }
        Map<String, Object> first = parsed.getFirst();
        Object tilesObj = first.get("tiles");
        Object reasoning = first.get(KEY_REASONING);
        Object confidence = first.get(KEY_CONFIDENCE);

        log.info("[IMG-GRID] LLM reasoning='{}' confidence={}", reasoning, confidence);

        if (!(tilesObj instanceof List<?> list)) return List.of();
        return parseTilesList(list, lastIndex);
    }


    public List<Integer> askLlmForTilesWithVoting(String instruction, String screenshotB64, int gridSize) {
        int minVotes = gridSize >= 4 ? 1 : 2;
        Map<Integer, Integer> voteCounts = new HashMap<>();

        for (int i = 0; i < VOTING_PASSES; i++) {
            List<Integer> tiles = askLlmForTiles(instruction, screenshotB64, gridSize);
            log.debug("[IMG-GRID-VOTE] Pass {}/{}: tiles={}", i + 1, VOTING_PASSES, tiles);
            for (Integer tile : tiles) {
                voteCounts.merge(tile, 1, Integer::sum);
            }
        }
        List<Integer> consensus = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() >= minVotes)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        log.info("[IMG-GRID-VOTE] Consensus tiles={} minVotes={} (votes: {})",
                consensus, minVotes, voteCounts);
        return consensus;
    }

    public List<Integer> askLlmForAnnotatedTiles(String instruction, String annotatedB64, int tileCount) {
        if (dumpGrids) {
            dumpAnnotated(annotatedB64);
        }

        int minVotes = 2;
        Map<Integer, Integer> voteCounts = new HashMap<>();

        for (int i = 0; i < VOTING_PASSES; i++) {
            List<Integer> tiles = askLlmForAnnotatedTilesOnce(instruction, annotatedB64, tileCount);
            log.debug("[IMG-GRID-VOTE] Annotated pass {}/{}: tiles={}", i + 1, VOTING_PASSES, tiles);
            for (Integer tile : tiles) {
                voteCounts.merge(tile, 1, Integer::sum);
            }
        }

        List<Integer> consensus = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() >= minVotes)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        log.info("[IMG-GRID-VOTE] Annotated consensus tiles={} minVotes={} (votes: {})",
                consensus, minVotes, voteCounts);
        return consensus;
    }

    private List<Integer> askLlmForAnnotatedTilesOnce(String instruction, String annotatedB64, int tileCount) {
        try {
            return doAnnotatedLlmCall(instruction, annotatedB64, tileCount);
        } catch (LlmRateLimitException rate) {
            log.warn("[IMG-GRID] Groq rate limit (annotated) — backing off {}ms (provider={})",
                    rate.getRetryAfter().toMillis(), rate.getProvider());
            sleepQuietly(rate.getRetryAfter());
            try {
                return doAnnotatedLlmCall(instruction, annotatedB64, tileCount);
            } catch (Exception retryFailed) {
                log.warn("[IMG-GRID] Annotated LLM call still failed after backoff: {}",
                        retryFailed.getMessage());
                return List.of();
            }
        } catch (Exception e) {
            log.warn("[IMG-GRID] Annotated tile selection failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Integer> doAnnotatedLlmCall(String instruction, String annotatedB64, int tileCount) {
        int lastIndex = tileCount - 1;
        String cleanInstruction = cleanInstruction(instruction);

        String prompt = """
                You are solving an hCaptcha image-selection challenge.
                The image shows %d separate tiles. Each tile has its index number printed
                in WHITE on a RED box in its TOP-LEFT corner, numbered 0 to %d.
                
                INSTRUCTION: "%s"
                
                RULES (apply strictly):
                - Report tiles using ONLY the printed RED numbers — never count cells yourself.
                - These are SEPARATE independent images, NOT one photo split into cells.
                - Select a tile only if it CLEARLY matches the instruction.
                - Blurred, dark, partial or ambiguous tiles -> EXCLUDE.
                - If NO tile clearly matches -> return an empty array [].
                
                ANTI-BIAS REMINDERS (very important):
                - The matching tile(s) can be ANYWHERE in the grid — top-left, edge,
                  corner, or centre. Do NOT default to the centre tile (index 4 of a
                  3x3) just because it is the safest-looking guess.
                - The challenge may have ONE match, MANY matches, or ZERO matches.
                  Do NOT assume there must be exactly one.
                - Each tile must be evaluated INDEPENDENTLY. Visit tile 0, then tile 1,
                  then tile 2, ... — actually look at each one before deciding.
                - If you are unsure, EXCLUDE the tile rather than guessing.
                
                Examples:
                - Matches: {"tiles": [0, 4], "confidence": 0.9, "reasoning": "trees at 0 and 4"}
                - Edge match: {"tiles": [8], "confidence": 0.85, "reasoning": "only tile 8 shows the target"}
                - None: {"tiles": [], "confidence": 0.95, "reasoning": "no matching tile"}
                
                Respond with ONLY a JSON object (no markdown, no prose):
                {"tiles": [<indices>], "confidence": <0.0-1.0>, "reasoning": "<brief>"}
                """.formatted(tileCount, lastIndex, cleanInstruction);

        // FIX: use IMAGE_PNG constant
        String raw = visionClient.analyzeImage(prompt, annotatedB64, IMAGE_PNG);
        List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
        if (parsed.isEmpty()) {
            log.warn("[IMG-GRID] Annotated LLM returned empty parse result");
            return List.of();
        }
        Map<String, Object> first = parsed.getFirst();
        Object tilesObj = first.get("tiles");
        log.info("[IMG-GRID] Annotated LLM reasoning='{}' confidence={}",
                first.get(KEY_REASONING), first.get(KEY_CONFIDENCE));

        if (!(tilesObj instanceof List<?> list)) return List.of();
        return parseTilesList(list, lastIndex);
    }


    public List<ClickPoint> askLlmForClickPoints(String instruction, String croppedB64) {
        if (dumpGrids) {
            dumpAnnotated(croppedB64);
        }

        List<List<ClickPoint>> passes = new ArrayList<>();
        for (int i = 0; i < VOTING_PASSES; i++) {
            List<ClickPoint> pts = askLlmForClickPointsOnce(instruction, croppedB64);
            log.debug("[IMG-GRID-COORD] Pass {}/{} points={}", i + 1, VOTING_PASSES, pts);
            passes.add(pts);
        }

        List<ClickPoint> consensus = clusterAndConsense(passes);

        log.info("[IMG-GRID-COORD] Consensus points={} minVotes={} (passes raw: {})",
                consensus, CLICK_MIN_VOTES, passes);

        if (dumpGrids && !consensus.isEmpty()) {
            dumpClickPoints(croppedB64, consensus);
        }

        return consensus;
    }

    private List<ClickPoint> askLlmForClickPointsOnce(String instruction, String b64) {
        try {
            return doClickPointsLlmCall(instruction, b64);
        } catch (LlmRateLimitException rate) {
            log.warn("[IMG-GRID] Groq rate limit (coord) — backing off {}ms (provider={})",
                    rate.getRetryAfter().toMillis(), rate.getProvider());
            sleepQuietly(rate.getRetryAfter());
            try {
                return doClickPointsLlmCall(instruction, b64);
            } catch (Exception retryFailed) {
                log.warn("[IMG-GRID] Coord LLM call still failed after backoff: {}",
                        retryFailed.getMessage());
                return List.of();
            }
        } catch (Exception e) {
            log.warn("[IMG-GRID] Coord LLM call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ClickPoint> doClickPointsLlmCall(String instruction, String b64) {
        String cleanInstruction = cleanInstruction(instruction);

        String prompt = """
                You are solving an hCaptcha image-selection challenge.
                The supplied image is a CROPPED view containing ONLY the tile grid.
                
                INSTRUCTION: "%s"
                
                For a 3x3 grid, tile centres are at x_pct ∈ {0.167, 0.500, 0.833}
                and y_pct ∈ {0.167, 0.500, 0.833}. Use ONLY these values verbatim.
                For a 4x4 grid, use {0.125, 0.375, 0.625, 0.875}.
                
                Do not default to the centre. Evaluate each tile independently.
                If no tile clearly matches, return an empty "targets" array.
                
                Respond with ONLY a JSON object:
                {"grid":"3x3","targets":[{"x_pct":0.167,"y_pct":0.500,"label":"tree"}],
                 "confidence":<0..1>,"reasoning":"<brief>"}
                """.formatted(cleanInstruction);
        String raw = visionClient.analyzeImage(prompt, b64, IMAGE_PNG);
        List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
        if (parsed.isEmpty()) return List.of();

        // FIX: use getFirst()
        Map<String, Object> first = parsed.getFirst();
        Object targetsObj = first.get("targets");
        log.info("[IMG-GRID] Coord LLM grid='{}' reasoning='{}' confidence={}",
                first.get("grid"), first.get(KEY_REASONING), first.get(KEY_CONFIDENCE));

        if (!(targetsObj instanceof List<?> list)) return List.of();

        List<ClickPoint> result = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Double x = toDouble(m.get("x_pct"));
                Double y = toDouble(m.get("y_pct"));
                Object lblObj = m.get("label");
                String label = lblObj == null ? "" : lblObj.toString();
                if (x != null && y != null) {
                    result.add(new ClickPoint(clamp01(x), clamp01(y), label));
                }
            }
        }
        return result;
    }

    private List<ClickPoint> clusterAndConsense(List<List<ClickPoint>> passes) {
        List<ClickPoint> all = new ArrayList<>();
        passes.forEach(all::addAll);
        if (all.isEmpty()) return List.of();

        List<List<ClickPoint>> clusters = new ArrayList<>();
        for (ClickPoint p : all) {
            boolean joined = false;
            for (List<ClickPoint> cluster : clusters) {
                ClickPoint c = centroid(cluster);
                if (distance(p, c) <= ImageGridSolverSupport.CLICK_CLUSTER_THRESHOLD) {
                    cluster.add(p);
                    joined = true;
                    break;
                }
            }
            if (!joined) {
                List<ClickPoint> fresh = new ArrayList<>();
                fresh.add(p);
                clusters.add(fresh);
            }
        }

        List<ClickPoint> consensus = new ArrayList<>();
        for (List<ClickPoint> cluster : clusters) {
            if (cluster.size() >= ImageGridSolverSupport.CLICK_MIN_VOTES) {
                consensus.add(centroid(cluster));
            }
        }
        return consensus;
    }

    private static ClickPoint centroid(List<ClickPoint> pts) {
        if (pts.isEmpty()) return new ClickPoint(0, 0, "");
        // FIX: declare sx and sy on separate lines
        double sx = 0;
        double sy = 0;
        for (ClickPoint p : pts) {
            sx += p.xPct();
            sy += p.yPct();
        }
        int n = pts.size();
        // FIX: use getFirst() instead of get(0)
        return new ClickPoint(sx / n, sy / n, pts.getFirst().label());
    }

    private static double distance(ClickPoint a, ClickPoint b) {
        double dx = a.xPct() - b.xPct();
        double dy = a.yPct() - b.yPct();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double clamp01(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }

    private void dumpAnnotated(String annotatedB64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(annotatedB64);
            // FIX: use DIR_TARGET and DIR_CAPTCHA_DEBUG constants
            Path dir = Files.createDirectories(Path.of(DIR_TARGET, DIR_CAPTCHA_DEBUG));
            Path file = dir.resolve("annotated-" + System.currentTimeMillis() + ".png");
            Files.write(file, bytes);
            log.info("[IMG-GRID] Debug annotated grid dumped → {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[IMG-GRID] Annotated dump failed: {}", e.getMessage());
        }
    }

    private void dumpClickPoints(String b64, List<ClickPoint> points) {
        try {
            BufferedImage img = ImageIO.read(
                    new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
            if (img == null) return;
            Graphics2D g = img.createGraphics();
            g.setStroke(new BasicStroke(3f));
            int r = Math.max(20, Math.min(img.getWidth(), img.getHeight()) / 20);
            for (ClickPoint cp : points) {
                int cx = (int) (cp.xPct() * img.getWidth());
                int cy = (int) (cp.yPct() * img.getHeight());
                g.setColor(Color.RED);
                g.drawOval(cx - r, cy - r, r * 2, r * 2);
                g.fillOval(cx - 4, cy - 4, 8, 8);
                if (cp.label() != null && !cp.label().isBlank()) {
                    g.setColor(Color.WHITE);
                    g.fillRect(cx + r, cy - r, 110, 18);
                    g.setColor(Color.BLACK);
                    g.drawString(cp.label(), cx + r + 4, cy - r + 14);
                }
            }
            g.dispose();
            Path dir = Files.createDirectories(Path.of(DIR_TARGET, DIR_CAPTCHA_DEBUG));
            Path file = dir.resolve("clicks-" + System.currentTimeMillis() + ".png");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            Files.write(file, out.toByteArray());
            log.info("[IMG-GRID] Debug click overlay dumped → {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[IMG-GRID] Click-points dump failed: {}", e.getMessage());
        }
    }


    private static String decisionPolicy(boolean splitImage) {
        if (splitImage) {
            return """
                    DECISION POLICY (this is ONE photo split into cells):
                    1. If a cell shows no part of the target at all -> EXCLUDE it.
                    2. INCLUDE every cell containing ANY visible part of the target --
                       even a small corner, edge, or thin sliver. Partial cells COUNT.
                    3. Missing a partial cell FAILS the challenge: when unsure about an
                       edge cell that plausibly touches the object, INCLUDE it.
                    4. Only exclude cells that clearly contain none of the target.
                    5. If NO cell contains the target -> return an empty array [].""";
        }
        return """
                DECISION POLICY (these are separate independent images):
                1. If you cannot clearly identify the target with the markers above -> EXCLUDE.
                2. Tiles where only a tiny corner of the target is visible -> EXCLUDE.
                3. Heavily blurred, dark, or obscured tiles -> EXCLUDE.
                4. False positives are worse than false negatives here.
                5. If NO tile clearly matches -> return an empty array [].""";
    }

    private String prepareGridImage(String screenshotB64, int gridSize) {
        try {
            BufferedImage img = ImageIO.read(
                    new ByteArrayInputStream(Base64.getDecoder().decode(screenshotB64)));
            if (img == null) return screenshotB64;

            Graphics2D g = img.createGraphics();
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            double cw = img.getWidth() / (double) gridSize;
            double ch = img.getHeight() / (double) gridSize;
            for (int i = 0; i < gridSize * gridSize; i++) {
                int x = (int) ((i % gridSize) * cw) + 4;
                int y = (int) (((double) i / gridSize) * ch) + 20;
                g.setColor(Color.RED);
                g.fillRect(x - 4, y - 16, 28, 21);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(i), x, y);
            }
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            byte[] numbered = out.toByteArray();

            if (dumpGrids) {
                Path dir = Files.createDirectories(Path.of(DIR_TARGET, DIR_CAPTCHA_DEBUG));
                Path file = dir.resolve("grid-" + System.currentTimeMillis() + ".png");
                Files.write(file, numbered);
                log.info("[IMG-GRID] Debug grid dumped → {}", file.toAbsolutePath());
            }
            return Base64.getEncoder().encodeToString(numbered);
        } catch (Exception e) {
            log.warn("[IMG-GRID] Grid numbering failed, using raw screenshot: {}", e.getMessage());
            return screenshotB64;
        }
    }

    private static String cleanInstruction(String raw) {
        if (raw == null) return "";
        return replacePattern(
                replacePattern(
                        replacePattern(
                                replacePattern(
                                        replacePattern(
                                                replacePattern(
                                                        replacePattern(
                                                                replacePattern(
                                                                        replacePattern(
                                                                                replacePattern(raw.replaceAll("\\s+", " "),
                                                                                        "Lorsque vous avez termin\u00e9[^.]*\\.?", ""),
                                                                                "When you're done[^.]*\\.?", ""),
                                                                        "When you are done[^.]*\\.?", ""),
                                                                "Click verify[^.]*\\.?", ""),
                                                        "Cliquez sur (?:le bouton )?(?:de )?valid[^.]*\\.?", ""),
                                                "S'il n'y en a aucune[^.]*", ""),
                                        "If there are none[^.]*", ""),
                                "cliquez sur \"Ignorer\"", ""),
                        "click \"Skip\"", ""),
                "\\s+", " ")
                .trim();
    }

    private static String replacePattern(String input, String regex, String replacement) {
        return Pattern.compile(regex,
                        Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ | Pattern.UNICODE_CHARACTER_CLASS)
                .matcher(input)
                .replaceAll(replacement);
    }

    private static List<Integer> parseTilesList(List<?> list, int lastIndex) {
        List<Integer> result = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                result.add(n.intValue());
            } else {
                try {
                    result.add(Integer.parseInt(o.toString().trim()));
                } catch (NumberFormatException ignored) {
                    // ignore non-numeric entries
                }
            }
        }
        result.removeIf(i -> i < 0 || i > lastIndex);
        return result;
    }

    private static int humanDelay(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}