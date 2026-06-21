package com.elyadata.feature.scraper.antibot.captcha.solvers.recaptcha;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
public class RecaptchaTileAnalyzer {

    private static final int TILE_INSET_PX = 4;
    private static final int CLASSIFY_TILE_SIZE_PX = 80;
    private static final int MIN_RAW_TILE_SIZE = 20;

    private boolean opencvReady = false;

    @PostConstruct
    void init() {
        try {
            OpenCV.loadShared();
            opencvReady = true;
            log.info("[RECAPTCHA-CV] OpenCV loaded (version={})", Core.getVersionString());
        } catch (Throwable t) {
            log.error("[RECAPTCHA-CV] Failed to load OpenCV: {} — cropping disabled",
                    t.getMessage());
            opencvReady = false;
        }
    }

    public boolean isReady() {
        return opencvReady;
    }

    /**
     * Crops the grid screenshot into individual tile PNGs (row-major, 0-indexed),
     * resized to CLASSIFY_TILE_SIZE_PX × CLASSIFY_TILE_SIZE_PX to minimise the
     * per-tile vision token cost when sent to the LLM.
     * Returns an empty list if OpenCV is unready or decoding fails.
     */
    public List<byte[]> cropTiles(byte[] gridPng, int gridSize) {
        if (!opencvReady || gridPng == null || gridPng.length == 0 || gridSize < 2) {
            return List.of();
        }
        Mat grid = null;
        try {
            grid = Imgcodecs.imdecode(new MatOfByte(gridPng), Imgcodecs.IMREAD_COLOR);
            if (grid.empty()) return List.of();

            int tileW = grid.cols() / gridSize;
            int tileH = grid.rows() / gridSize;
            if (tileW < MIN_RAW_TILE_SIZE || tileH < MIN_RAW_TILE_SIZE) {
                log.warn("[RECAPTCHA-CV] Tiles too small to crop ({}x{})", tileW, tileH);
                return List.of();
            }

            int totalTiles = gridSize * gridSize;
            List<byte[]> out = new ArrayList<>(totalTiles);
            Size targetSize = new Size(CLASSIFY_TILE_SIZE_PX, CLASSIFY_TILE_SIZE_PX);

            for (int i = 0; i < totalTiles; i++) {
                int row = i / gridSize;
                int col = i % gridSize;
                int x = col * tileW + TILE_INSET_PX;
                int y = row * tileH + TILE_INSET_PX;
                int w = Math.max(1, Math.min(tileW - 2 * TILE_INSET_PX, grid.cols() - x));
                int h = Math.max(1, Math.min(tileH - 2 * TILE_INSET_PX, grid.rows() - y));

                Mat tile = new Mat(grid, new Rect(x, y, w, h));
                Mat resized = new Mat();
                MatOfByte buf = new MatOfByte();
                try {
                    Imgproc.resize(tile, resized, targetSize, 0, 0, Imgproc.INTER_AREA);
                    Imgcodecs.imencode(".png", resized, buf);
                    out.add(buf.toArray());
                } finally {
                    resized.release();
                    buf.release();
                    // 'tile' is a view on grid — no release needed
                }
            }

            log.debug("[RECAPTCHA-CV] Cropped grid into {} tiles → resized to {}x{}",
                    out.size(), CLASSIFY_TILE_SIZE_PX, CLASSIFY_TILE_SIZE_PX);
            return out;

        } catch (Throwable t) {
            log.warn("[RECAPTCHA-CV] cropTiles threw: {}", t.getMessage());
            return List.of();
        } finally {
            if (grid != null) grid.release();
        }
    }
}