package com.elyadata.feature.scraper.antibot.captcha.solvers.turnstile;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.geom.Point2D;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class TurnstileMatcher {

    private static final int CHECKBOX_X_OFFSET = 30;

    @ConfigProperty(name = "turnstile.matcher.threshold", defaultValue = "0.72")
    double threshold;

    private Mat lightTemplate;
    private Mat darkTemplate;
    @Getter
    private boolean ready = false;

    @PostConstruct
    void init() {
        try {
            OpenCV.loadShared();
            lightTemplate = loadTemplate("/captcha-templates/turnstile_light.png");
            darkTemplate = loadTemplate("/captcha-templates/turnstile_dark.png");
            ready = (lightTemplate != null || darkTemplate != null);
            log.info("[TURNSTILE-MATCH] ready={} light={} dark={} threshold={}",
                    ready, lightTemplate != null, darkTemplate != null, threshold);
        } catch (Throwable t) {
            log.error("[TURNSTILE-MATCH] OpenCV init failed: {}", t.getMessage());
        }
    }

    private Mat loadTemplate(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("[TURNSTILE-MATCH] Template missing: {}", resourcePath);
                return null;
            }
            byte[] bytes = is.readAllBytes();
            Mat m = Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_COLOR);
            return m.empty() ? null : m;
        } catch (Exception e) {
            log.warn("[TURNSTILE-MATCH] load {} failed: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    public Optional<Point2D> findCheckbox(byte[] screenshotPng) {
        if (!ready || screenshotPng == null || screenshotPng.length == 0) return Optional.empty();

        Mat screenshot = null;
        Mat result = null;
        try {
            screenshot = Imgcodecs.imdecode(new MatOfByte(screenshotPng), Imgcodecs.IMREAD_COLOR);
            if (screenshot.empty()) return Optional.empty();

            double bestScore = 0;
            org.opencv.core.Point bestLoc = null;
            Mat bestTpl = null;

            for (Mat tpl : List.of(lightTemplate == null ? new Mat() : lightTemplate,
                    darkTemplate == null ? new Mat() : darkTemplate)) {
                if (tpl.empty()) continue;
                if (screenshot.rows() < tpl.rows() || screenshot.cols() < tpl.cols()) continue;

                result = new Mat();
                Imgproc.matchTemplate(screenshot, tpl, result, Imgproc.TM_CCOEFF_NORMED);
                Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
                if (mmr.maxVal > bestScore && mmr.maxVal >= threshold) {
                    bestScore = mmr.maxVal;
                    bestLoc = mmr.maxLoc;
                    bestTpl = tpl;
                }
                result.release();
                result = null;
            }

            if (bestLoc == null) {
                log.debug("[TURNSTILE-MATCH] No match above threshold {}", threshold);
                return Optional.empty();
            }

            double x = bestLoc.x + CHECKBOX_X_OFFSET;
            double y = bestLoc.y + bestTpl.rows() / 2.0;
            log.info("[TURNSTILE-MATCH] match score={} click=({}, {})",
                    String.format("%.3f", bestScore), (int) x, (int) y);
            return Optional.of(new Point2D.Double(x, y));
        } catch (Throwable t) {
            log.warn("[TURNSTILE-MATCH] failed: {}", t.getMessage());
            return Optional.empty();
        } finally {
            if (screenshot != null) screenshot.release();
            if (result != null) result.release();
        }
    }
}