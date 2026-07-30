package com.elyadata.feature.scraper.antibot.captcha.solvers.hcaptcha;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class HcaptchaSlotAnalyzer {

    private static final int[] GRID_SIZES_TO_TRY = {3, 4, 5, 2};
    private static final double OUTLIER_SCORE_THRESHOLD = 1.6;
    private static final double CANNY_LOW = 50;
    private static final double CANNY_HIGH = 150;
    private static final int MIN_CELL_SIZE = 30;

    @Getter
    private boolean ready = false;

    @PostConstruct
    void init() {
        try {
            OpenCV.loadShared();
            ready = true;
            log.info("[HCAPTCHA-CV] OpenCV native library loaded (version={})",
                    Core.getVersionString());
        } catch (Throwable t) {
            log.error("[HCAPTCHA-CV] OpenCV load failed: {} — slot detection disabled",
                    t.getMessage());
        }
    }

    public Optional<SlotPosition> findEmptySlot(byte[] canvasPng) {
        if (!ready || canvasPng == null || canvasPng.length == 0) return Optional.empty();

        Mat full = null;
        try {
            full = Imgcodecs.imdecode(new MatOfByte(canvasPng), Imgcodecs.IMREAD_COLOR);
            if (full.empty()) return Optional.empty();

            SlotPosition best = null;
            double bestScore = OUTLIER_SCORE_THRESHOLD;
            int bestGrid = 0;

            for (int n : GRID_SIZES_TO_TRY) {
                ScoredSlot c = analyzeGrid(full, n);
                if (c != null && c.score() > bestScore) {
                    bestScore = c.score();
                    best = c.position();
                    bestGrid = n;
                }
            }

            if (best != null) {
                log.info("[HCAPTCHA-CV] Slot at ({}%, {}%) on {}x{} grid score={}",
                        String.format("%.1f", best.xPct()),
                        String.format("%.1f", best.yPct()),
                        bestGrid, bestGrid,
                        String.format("%.2f", bestScore));
            } else {
                log.info("[HCAPTCHA-CV] No confident slot (threshold={})",
                        OUTLIER_SCORE_THRESHOLD);
            }
            return Optional.ofNullable(best);

        } catch (Throwable t) {
            log.warn("[HCAPTCHA-CV] findEmptySlot threw: {}", t.getMessage());
            return Optional.empty();
        } finally {
            if (full != null) full.release();
        }
    }

    private ScoredSlot analyzeGrid(Mat img, int n) {
        Mat gray = null;
        Mat edges = null;
        try {
            int cellW = img.cols() / n;
            int cellH = img.rows() / n;
            if (cellW < MIN_CELL_SIZE || cellH < MIN_CELL_SIZE) return null;

            gray = new Mat();
            Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
            edges = new Mat();
            Imgproc.Canny(gray, edges, CANNY_LOW, CANNY_HIGH);

            double[][] features = new double[n * n][3];
            for (int row = 0; row < n; row++) {
                for (int col = 0; col < n; col++) {
                    int idx = row * n + col;
                    Rect rect = new Rect(col * cellW, row * cellH, cellW, cellH);

                    Mat cellGray = new Mat(gray, rect);
                    Mat cellEdges = new Mat(edges, rect);

                    Scalar meanIntensity = Core.mean(cellGray);
                    Scalar edgeDensity = Core.mean(cellEdges);
                    MatOfDouble meanMat = new MatOfDouble();
                    MatOfDouble stdMat = new MatOfDouble();
                    Core.meanStdDev(cellGray, meanMat, stdMat);

                    features[idx][0] = meanIntensity.val[0];
                    features[idx][1] = edgeDensity.val[0] * 5.0;
                    features[idx][2] = stdMat.toArray()[0];

                    cellGray.release();
                    cellEdges.release();
                    meanMat.release();
                    stdMat.release();
                }
            }

            double[] median = perDimensionMedian(features);

            int outlierIdx = -1;
            double maxDist = 0;
            double sumDist = 0;
            for (int i = 0; i < n * n; i++) {
                double d = l2(features[i], median);
                sumDist += d;
                if (d > maxDist) {
                    maxDist = d;
                    outlierIdx = i;
                }
            }
            if (outlierIdx < 0) return null;

            double otherAvg = (sumDist - maxDist) / (n * n - 1);
            double score = otherAvg > 1.0 ? maxDist / otherAvg : 0;

            int row = outlierIdx / n;
            int col = outlierIdx % n;
            double xPct = ((col + 0.5) * cellW) / (double) img.cols() * 100;
            double yPct = ((row + 0.5) * cellH) / (double) img.rows() * 100;

            return new ScoredSlot(new SlotPosition(xPct, yPct, n), score);

        } finally {
            if (gray != null) gray.release();
            if (edges != null) edges.release();
        }
    }

    private static double[] perDimensionMedian(double[][] features) {
        int dim = features[0].length;
        int n = features.length;
        double[] median = new double[dim];
        for (int d = 0; d < dim; d++) {
            double[] values = new double[n];
            for (int i = 0; i < n; i++) values[i] = features[i][d];
            Arrays.sort(values);
            median[d] = values[n / 2];
        }
        return median;
    }

    private static double l2(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            s += d * d;
        }
        return Math.sqrt(s);
    }

    public record SlotPosition(double xPct, double yPct, int gridSize) {
    }

    private record ScoredSlot(SlotPosition position, double score) {
    }
}