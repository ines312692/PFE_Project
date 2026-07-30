package com.elyadata.feature.scraper.antibot.captcha.solvers.hcaptcha;

import com.elyadata.feature.scraper.antibot.captcha.solvers.support.ImageGridSolverSupport;
import com.elyadata.feature.scraper.antibot.captcha.solvers.support.ImageGridSolverSupport.ClickPoint;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
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
import java.util.regex.Pattern;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HcaptchaImageSolver {

    private static final int MAX_ROUNDS = 3;
    private static final int POST_CHECKBOX_WAIT_MS = 4_000;
    private static final int POST_VERIFY_WAIT_MS = 4_000;
    private static final int PRE_CHECKBOX_MIN_MS = 2_000;
    private static final int PRE_CHECKBOX_JITTER_MS = 2_000;
    private static final int CLICK_DELAY_MIN_MS = 400;
    private static final int CLICK_DELAY_JITTER_MS = 300;
    private static final int MAX_CLICKS = 9;
    private static final int CHALLENGE_RENDER_WAIT_MS = 1_500;
    private static final int GRID_SIZE = 3;
    private static final double DEFAULT_GRID_TOP_RATIO = 0.20;
    private static final double DEFAULT_GRID_BOTTOM_RATIO = 0.88;
    private static final double GRID_SIDE_RATIO = 0.02;
    private static final int GRID_PADDING_PX = 8;
    private static final double MIN_GRID_HEIGHT_RATIO = 0.30;

    private static final Pattern DRAG_CHALLENGE_PATTERN = Pattern.compile(
            "glisser|drag|déplacer|déposer|faites coulisser",
            Pattern.CASE_INSENSITIVE);

    private static final String[] CHECKBOX_IFRAME_SELECTORS = {
            "iframe[src*='hcaptcha.com'][src*='frame=checkbox']",
            "iframe[title*='hCaptcha'][title*='checkbox']",
            "iframe[title*='case à cocher']",
            "iframe[src*='newassets.hcaptcha.com'][src*='checkbox']",
            "iframe[data-hcaptcha-widget-id]",
            "iframe[title*='checkbox for hCaptcha']"
    };

    private static final String CHECKBOX_INNER_SELECTOR =
            "#checkbox, #anchor, [role='checkbox'], div[role='checkbox']";

    private static final String[] CHALLENGE_IFRAME_SELECTORS = {
            "iframe[src*='hcaptcha.com'][src*='frame=challenge']",
            "iframe[title*='hCaptcha'][title*='challenge']",
            "iframe[title*='Défi hCaptcha']",
            "iframe[title*='défi hCaptcha']",
            "iframe[src*='newassets.hcaptcha.com']:not([src*='checkbox'])",
            "iframe[title*='Main content of the hCaptcha challenge']"
    };

    private static final String PROMPT_SELECTOR =
            ".prompt-text, .challenge-prompt, h2.prompt, [class*='prompt']";

    private static final String VERIFY_SELECTOR =
            ".button-submit, .check, button[type='submit'], .challenge-actions button, " +
                    "[class*='button-submit'], [class*='verify'], [class*='check-button']";

    private final ImageGridSolverSupport support;
    private final HcaptchaDragSolver dragSolver;

    public Optional<String> solve(Page page, UUID jobId) {
        try {
            log.info("[HCAPTCHA] Starting solve job={}", jobId);

            page.waitForTimeout(PRE_CHECKBOX_MIN_MS
                    + ThreadLocalRandom.current().nextInt(0, PRE_CHECKBOX_JITTER_MS));

            logAllIframes(page, jobId);

            Locator checkbox = findCheckbox(page, jobId);
            if (checkbox == null) {
                log.warn("[HCAPTCHA] No hCaptcha checkbox found across all selectors job={}", jobId);
                return Optional.empty();
            }

            support.clickHumanlike(page, checkbox);
            log.info("[HCAPTCHA] Checkbox clicked, waiting for response job={}", jobId);

            page.waitForTimeout(POST_CHECKBOX_WAIT_MS);

            String token = readToken(page);
            if (token != null && !token.isBlank()) {
                log.info("[HCAPTCHA] Solved instantly without challenge (score-based) job={}", jobId);
                logTokenPreview(token);
                return Optional.of(token);
            }

            log.info("[HCAPTCHA] No instant token, challenge expected job={}", jobId);

            for (int round = 1; round <= MAX_ROUNDS; round++) {
                log.info("[HCAPTCHA] Round {}/{} job={}", round, MAX_ROUNDS, jobId);
                if (!solveOneRound(page, jobId)) {
                    log.warn("[HCAPTCHA] Round {} failed job={}", round, jobId);
                    return Optional.empty();
                }
                page.waitForTimeout(POST_VERIFY_WAIT_MS);

                token = readToken(page);
                if (token != null && !token.isBlank()) {
                    log.info("[HCAPTCHA] Solved after {} round(s) job={}", round, jobId);
                    logTokenPreview(token);
                    return Optional.of(token);
                }
            }

            log.warn("[HCAPTCHA] Exhausted {} rounds job={}", MAX_ROUNDS, jobId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[HCAPTCHA] Solve failed job={}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    private Locator findCheckbox(Page page, UUID jobId) {
        for (String iframeSel : CHECKBOX_IFRAME_SELECTORS) {
            try {
                FrameLocator candidate = page.frameLocator(iframeSel);
                Locator cb = candidate.locator(CHECKBOX_INNER_SELECTOR).first();
                cb.waitFor(new Locator.WaitForOptions().setTimeout(3_000));
                log.info("[HCAPTCHA] Checkbox found via iframe selector: {} job={}", iframeSel, jobId);
                return cb;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private FrameLocator findChallengeFrame(Page page, UUID jobId) {
        for (String iframeSel : CHALLENGE_IFRAME_SELECTORS) {
            try {
                FrameLocator candidate = page.frameLocator(iframeSel);
                Locator promptCheck = candidate.locator(PROMPT_SELECTOR).first();
                promptCheck.waitFor(new Locator.WaitForOptions().setTimeout(4_000));
                log.info("[HCAPTCHA] Challenge frame found via selector: {} job={}", iframeSel, jobId);
                return candidate;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean solveOneRound(Page page, UUID jobId) {
        FrameLocator challenge = findChallengeFrame(page, jobId);
        if (challenge == null) {
            String token = readToken(page);
            if (token != null && !token.isBlank()) {
                log.info("[HCAPTCHA] Token appeared while searching for challenge frame job={}", jobId);
                return true;
            }
            log.warn("[HCAPTCHA] Challenge frame not found job={}", jobId);
            return false;
        }

        Locator promptLoc = challenge.locator(PROMPT_SELECTOR).first();
        String instruction;
        try {
            instruction = promptLoc.innerText().trim();
        } catch (Exception e) {
            log.warn("[HCAPTCHA] Failed to read prompt: {} job={}", e.getMessage(), jobId);
            return false;
        }

        if (DRAG_CHALLENGE_PATTERN.matcher(instruction).find()) {
            log.info("[HCAPTCHA] Drag-puzzle challenge detected: '{}' — routing to drag solver job={}",
                    instruction.replaceAll("\\s+", " "), jobId);
            boolean dragged = dragSolver.solve(page, challenge, instruction, jobId);
            if (!dragged) {
                log.warn("[HCAPTCHA] Drag solver failed job={}", jobId);
                return false;
            }
            clickVerify(page, challenge);
            return true;
        }

        page.waitForTimeout(CHALLENGE_RENDER_WAIT_MS);

        Locator body = challenge.locator("body").first();
        Locator verifyLoc = challenge.locator(VERIFY_SELECTOR).first();
        BoundingBox bodyBox;
        BoundingBox promptBox = null;
        BoundingBox verifyBox = null;
        byte[] screenshot;
        try {
            bodyBox = body.boundingBox();
            screenshot = body.screenshot();
            try {
                promptBox = promptLoc.boundingBox();
            } catch (Exception ignored) {
            }
            try {
                verifyBox = verifyLoc.boundingBox();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log.warn("[HCAPTCHA] Failed to capture challenge body screenshot job={}: {}",
                    jobId, e.getMessage());
            return false;
        }

        if (bodyBox == null || screenshot == null || screenshot.length == 0) {
            log.warn("[HCAPTCHA] Missing body bounding box or screenshot bytes job={}", jobId);
            return false;
        }
        double cropTopRatio = DEFAULT_GRID_TOP_RATIO;
        double cropBottomRatio = DEFAULT_GRID_BOTTOM_RATIO;
        if (promptBox != null) {
            double t = (promptBox.y + promptBox.height - bodyBox.y + GRID_PADDING_PX) / bodyBox.height;
            cropTopRatio = clamp01(t);
        }
        if (verifyBox != null) {
            double b = (verifyBox.y - bodyBox.y - GRID_PADDING_PX) / bodyBox.height;
            cropBottomRatio = clamp01(b);
        }
        if (cropBottomRatio - cropTopRatio < MIN_GRID_HEIGHT_RATIO) {
            log.warn("[HCAPTCHA] Computed crop too thin ({} → {}) — falling back to defaults job={}",
                    cropTopRatio, cropBottomRatio, jobId);
            cropTopRatio = DEFAULT_GRID_TOP_RATIO;
            cropBottomRatio = DEFAULT_GRID_BOTTOM_RATIO;
        }
        double cropLeftRatio = GRID_SIDE_RATIO;
        double cropRightRatio = 1.0 - GRID_SIDE_RATIO;
        byte[] croppedBytes;
        int imgW;
        int imgH;
        try {
            BufferedImage full = ImageIO.read(new ByteArrayInputStream(screenshot));
            if (full == null) {
                log.warn("[HCAPTCHA] Could not decode body screenshot bytes job={}", jobId);
                return false;
            }
            imgW = full.getWidth();
            imgH = full.getHeight();
            int cx = Math.max(0, (int) (imgW * cropLeftRatio));
            int cy = Math.max(0, (int) (imgH * cropTopRatio));
            int cw = Math.min(imgW - cx, (int) (imgW * (cropRightRatio - cropLeftRatio)));
            int ch = Math.min(imgH - cy, (int) (imgH * (cropBottomRatio - cropTopRatio)));
            BufferedImage cropped = full.getSubimage(cx, cy, cw, ch);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", out);
            croppedBytes = out.toByteArray();
        } catch (Exception e) {
            log.warn("[HCAPTCHA] Crop failed job={}: {}", jobId, e.getMessage());
            return false;
        }

        String annotatedB64 = annotateGridIndices(croppedBytes, GRID_SIZE);
        if (annotatedB64 == null) {
            log.warn("[HCAPTCHA] Failed to annotate cropped grid job={}", jobId);
            return false;
        }

        log.info("[HCAPTCHA] Challenge='{}' bodyBox=({}x{} @ {},{}) crop=y[{}..{}] x[{}..{}] " +
                        "img={}x{} grid={}x{} job={}",
                instruction.replaceAll("\\s+", " "),
                (int) bodyBox.width, (int) bodyBox.height,
                (int) bodyBox.x, (int) bodyBox.y,
                String.format("%.3f", cropTopRatio), String.format("%.3f", cropBottomRatio),
                String.format("%.3f", cropLeftRatio), String.format("%.3f", cropRightRatio),
                imgW, imgH, GRID_SIZE, GRID_SIZE, jobId);
        int tileCount = GRID_SIZE * GRID_SIZE;
        List<Integer> indices = support.askLlmForAnnotatedTiles(instruction, annotatedB64, tileCount);

        if (indices.isEmpty()) {
            log.info("[HCAPTCHA] LLM consensus: no matching tiles — clicking Verify directly job={}", jobId);
            clickVerify(page, challenge);
            return true;
        }
        List<ClickPoint> toClick = new ArrayList<>();
        for (int idx : indices) {
            if (idx < 0 || idx >= tileCount) {
                log.warn("[HCAPTCHA] LLM returned out-of-range tile index {} (expected 0..{}) — skipped job={}",
                        idx, tileCount - 1, jobId);
                continue;
            }
            int row = idx / GRID_SIZE;
            int col = idx % GRID_SIZE;
            double xPct = (col + 0.5) / GRID_SIZE;
            double yPct = (row + 0.5) / GRID_SIZE;
            toClick.add(new ClickPoint(xPct, yPct, "tile-" + idx));
            if (toClick.size() >= MAX_CLICKS) break;
        }

        if (toClick.isEmpty()) {
            log.warn("[HCAPTCHA] All LLM indices invalid — clicking Verify with no selection job={}", jobId);
            clickVerify(page, challenge);
            return true;
        }

        log.info("[HCAPTCHA] LLM picked tiles {} → cropped centres {} job={}", indices, toClick, jobId);
        for (ClickPoint cp : toClick) {
            double bodyXPct = cropLeftRatio + cp.xPct() * (cropRightRatio - cropLeftRatio);
            double bodyYPct = cropTopRatio + cp.yPct() * (cropBottomRatio - cropTopRatio);
            double pageX = bodyBox.x + bodyXPct * bodyBox.width;
            double pageY = bodyBox.y + bodyYPct * bodyBox.height;
            log.debug("[HCAPTCHA] {} → crop=({},{}) → body=({},{}) → viewport=({},{}) job={}",
                    cp.label(),
                    String.format("%.3f", cp.xPct()), String.format("%.3f", cp.yPct()),
                    String.format("%.3f", bodyXPct), String.format("%.3f", bodyYPct),
                    (int) pageX, (int) pageY, jobId);
            support.clickHumanlikeAt(page, pageX, pageY);
            page.waitForTimeout(CLICK_DELAY_MIN_MS
                    + ThreadLocalRandom.current().nextInt(0, CLICK_DELAY_JITTER_MS));
        }

        clickVerify(page, challenge);
        return true;
    }

    private String annotateGridIndices(byte[] croppedBytes, int gridSize) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(croppedBytes));
            if (img == null) return null;

            Graphics2D g = img.createGraphics();
            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            double cw = img.getWidth() / (double) gridSize;
            double ch = img.getHeight() / (double) gridSize;
            for (int i = 0; i < gridSize * gridSize; i++) {
                int row = i / gridSize;
                int col = i % gridSize;
                int x = (int) (col * cw) + 6;
                int y = (int) (row * ch) + 24;
                g.setColor(Color.RED);
                g.fillRect(x - 4, y - 18, 32, 24);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(i), x, y);
            }
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.warn("[HCAPTCHA] Grid annotation failed: {}", e.getMessage());
            return null;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void clickVerify(Page page, FrameLocator challenge) {
        Locator verify = challenge.locator(VERIFY_SELECTOR).first();
        try {
            support.clickHumanlike(page, verify);
        } catch (Exception e) {
            log.warn("[HCAPTCHA] Verify button click failed: {}", e.getMessage());
        }
    }

    private void logAllIframes(Page page, UUID jobId) {
        try {
            Object iframes = page.evaluate("""
                    () => Array.from(document.querySelectorAll('iframe')).map(f => ({
                      src: f.src ? f.src.substring(0, 100) : '(no-src)',
                      title: f.title || '(no-title)',
                      id: f.id || '(no-id)',
                      name: f.name || '(no-name)',
                      'data-hcaptcha-widget-id': f.getAttribute('data-hcaptcha-widget-id') || null
                    }))
                    """);
            log.info("[HCAPTCHA-DIAG] stage={} iframes={} job={}", "before-checkbox-click", iframes, jobId);
        } catch (Exception ignored) {
        }
    }

    private void logTokenPreview(String token) {
        log.info("[HCAPTCHA] Token preview: {}... (len={})",
                token.substring(0, Math.min(40, token.length())), token.length());
    }

    private String readToken(Page page) {
        try {
            Object val = page.evaluate("""
                    () => {
                      const ta = document.querySelector(
                        'textarea[name="h-captcha-response"], ' +
                        'textarea[id="h-captcha-response"], ' +
                        'textarea[name="g-recaptcha-response"]');
                      if (ta && ta.value) return ta.value;
                      const allTextareas = document.querySelectorAll('textarea');
                      for (const t of allTextareas) {
                        if (t.name && t.name.includes('captcha') && t.value) return t.value;
                      }
                      return '';
                    }
                    """);
            return val == null ? null : val.toString();
        } catch (Exception e) {
            return null;
        }
    }
}