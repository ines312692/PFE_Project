package com.elyadata.feature.scraper.antibot.captcha.solvers.llm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


@Slf4j
@ApplicationScoped
public class CaptchaImagePreprocessor {

    private static final int MIN_WIDTH = 400;
    private static final int MEDIAN_RADIUS = 1;

    public byte[] enhance(byte[] raw) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
        if (img == null) {
            throw new IOException("CAPTCHA image unreadable / unsupported format");
        }
        BufferedImage upscaled = upscale(img);
        BufferedImage gray = toGrayscale(upscaled);
        BufferedImage denoised = medianFilter(gray);
        BufferedImage binarized = otsuBinarize(denoised);
        BufferedImage cleaned = morphologicalOpening(binarized);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(cleaned, "png", out);
        return out.toByteArray();
    }

    private BufferedImage upscale(BufferedImage src) {
        if (src.getWidth() >= MIN_WIDTH) return src;
        int h = Math.round(src.getHeight() * ((float) MIN_WIDTH / src.getWidth()));
        BufferedImage out = new BufferedImage(MIN_WIDTH, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src.getScaledInstance(MIN_WIDTH, h, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    private BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage out = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
    private int[] sampleSortedWindow(BufferedImage src, int x, int y) {
        int w = src.getWidth();
        int h = src.getHeight();
        int windowSize = (2 * MEDIAN_RADIUS + 1) * (2 * MEDIAN_RADIUS + 1);
        int[] window = new int[windowSize];
        int k = 0;
        for (int dy = -MEDIAN_RADIUS; dy <= MEDIAN_RADIUS; dy++) {
            for (int dx = -MEDIAN_RADIUS; dx <= MEDIAN_RADIUS; dx++) {
                int nx = Math.clamp(x +(long) dx, 0, w - 1);
                int ny = Math.clamp(y +(long) dy, 0, h - 1);
                window[k++] = src.getRaster().getSample(nx, ny, 0);
            }
        }
        java.util.Arrays.sort(window);
        return window;
    }

    private BufferedImage medianFilter(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int windowSize = (2 * MEDIAN_RADIUS + 1) * (2 * MEDIAN_RADIUS + 1);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int[] window = sampleSortedWindow(src, x, y);
                out.getRaster().setSample(x, y, 0, window[windowSize / 2]);
            }
        }
        return out;
    }

    private BufferedImage otsuBinarize(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] histogram = new int[256];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                histogram[src.getRaster().getSample(x, y, 0)]++;
            }
        }

        int threshold = getThreshold(w, h, histogram);

        log.debug("[CAPTCHA-PREPROC] Otsu threshold = {}", threshold);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = src.getRaster().getSample(x, y, 0);
                out.getRaster().setSample(x, y, 0, v > threshold ? 255 : 0);
            }
        }
        return out;
    }

    private static int getThreshold(int w, int h, int[] histogram) {
        int total = w * h;
        float sum = 0;
        for (int t = 0; t < 256; t++) sum += t * histogram[t];

        float sumB = 0;
        int wB = 0;
        float maxVariance = 0;
        int threshold = 0;
        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            int wF = total - wB;
            if (wB > 0 && wF > 0) {
                sumB += t * histogram[t];
                float mB = sumB / wB;
                float mF = (sum - sumB) / wF;
                float variance = (mB - mF) * (mB - mF) * wB * wF;
                if (variance > maxVariance) {
                    maxVariance = variance;
                    threshold = t;
                }
            }
        }
        return threshold;
    }

    private BufferedImage morphologicalOpening(BufferedImage src) {
        BufferedImage eroded = erode(src);
        return dilate(eroded);
    }

    private BufferedImage erode(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int min = 255;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int v = src.getRaster().getSample(x + dx, y + dy, 0);
                        if (v < min) min = v;
                    }
                }
                out.getRaster().setSample(x, y, 0, min);
            }
        }
        return out;
    }

    private BufferedImage dilate(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int max = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int v = src.getRaster().getSample(x + dx, y + dy, 0);
                        if (v > max) max = v;
                    }
                }
                out.getRaster().setSample(x, y, 0, max);
            }
        }
        return out;
    }
}