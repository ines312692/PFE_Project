package com.elyadata.feature.scraper.antibot.captcha.solvers.geetest;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Optional;

@Slf4j
@ApplicationScoped
public class GeetestImageAnalyzer {

    private static final int PIECE_REGION_WIDTH = 70;
    private static final double CANNY_LOW = 50;
    private static final double CANNY_HIGH = 150;
    private static final int PIECE_VERTICAL_PADDING = 5;

    private boolean opencvReady = false;

    @PostConstruct
    void init() {
        try {
            OpenCV.loadShared();
            opencvReady = true;
            log.info("[GEETEST-CV] OpenCV native library loaded (version={})",
                    Core.getVersionString());
        } catch (Throwable t) {
            log.error("[GEETEST-CV] Failed to load OpenCV: {} — solver will use LLM only",
                    t.getMessage());
            opencvReady = false;
        }
    }

    public boolean isReady() {
        return opencvReady;
    }

    public Optional<DetectionResult> detectHolePosition(byte[] puzzlePng) {
        if (!opencvReady || puzzlePng == null || puzzlePng.length == 0) {
            return Optional.empty();
        }

        Mat full = null;
        Mat gray = null;
        Mat edges = null;
        Mat leftStrip = null;
        Mat pieceMat = null;
        Mat searchMat = null;
        Mat result = null;

        try {
            full = Imgcodecs.imdecode(new MatOfByte(puzzlePng), Imgcodecs.IMREAD_COLOR);
            if (full.empty()) {
                log.warn("[GEETEST-CV] Could not decode PNG bytes");
                return Optional.empty();
            }
            int width = full.cols();
            int height = full.rows();
            gray = new Mat();
            Imgproc.cvtColor(full, gray, Imgproc.COLOR_BGR2GRAY);
            edges = new Mat();
            Imgproc.Canny(gray, edges, CANNY_LOW, CANNY_HIGH);
            int stripWidth = Math.min(PIECE_REGION_WIDTH, width / 4);
            leftStrip = new Mat(edges, new Rect(0, 0, stripWidth, height));
            Rect pieceBbox = findNonZeroBbox(leftStrip);

            if (pieceBbox == null || pieceBbox.width < 15 || pieceBbox.height < 15) {
                log.warn("[GEETEST-CV] Piece bbox not found or too small (w={}, h={})",
                        pieceBbox == null ? "?" : pieceBbox.width,
                        pieceBbox == null ? "?" : pieceBbox.height);
                return Optional.empty();
            }
            int yStart = Math.max(0, pieceBbox.y - PIECE_VERTICAL_PADDING);
            int yEnd = Math.min(height, pieceBbox.y + pieceBbox.height + PIECE_VERTICAL_PADDING);
            int pieceH = yEnd - yStart;
            Rect pieceTemplateRect = new Rect(pieceBbox.x, yStart, pieceBbox.width, pieceH);
            pieceMat = new Mat(edges, pieceTemplateRect).clone();
            int searchX0 = stripWidth;
            if (searchX0 + pieceBbox.width >= width) {
                log.warn("[GEETEST-CV] Search area too narrow (image width={})", width);
                return Optional.empty();
            }
            Rect searchRect = new Rect(searchX0, yStart, width - searchX0, pieceH);
            searchMat = new Mat(edges, searchRect).clone();
            result = new Mat();
            Imgproc.matchTemplate(searchMat, pieceMat, result, Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

            double score = mmr.maxVal;
            int holeXInSearch = (int) mmr.maxLoc.x;
            int holeXAbsolute = holeXInSearch + searchX0;

            log.info("[GEETEST-CV] holeX_abs={}px pieceX={}px score={} (info-only, no gate)",
                    holeXAbsolute, pieceBbox.x, String.format("%.3f", score));
            int sliderOffset = holeXAbsolute - pieceBbox.x;
            if (sliderOffset < 10 || sliderOffset > width) {
                log.warn("[GEETEST-CV] Computed offset {}px out of plausible range", sliderOffset);
                return Optional.empty();
            }

            return Optional.of(new DetectionResult(sliderOffset, score));

        } catch (Throwable t) {
            log.warn("[GEETEST-CV] Detection threw: {}", t.getMessage());
            return Optional.empty();
        } finally {
            release(full, gray, edges, leftStrip, pieceMat, searchMat, result);
        }
    }

    private static Rect findNonZeroBbox(Mat binary) {
        Mat points = new Mat();
        try {
            Core.findNonZero(binary, points);
            if (points.empty()) return null;
            return Imgproc.boundingRect(points);
        } finally {
            points.release();
        }
    }

    private static void release(Mat... mats) {
        for (Mat m : mats) {
            if (m != null) m.release();
        }
    }

    public record DetectionResult(int sliderOffsetPx, double correlationScore) {
    }
}