package com.elyadata.feature.scraper.browser.humanizer.motion;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class MousePathGenerator {


    public record Point(double x, double y) {
    }

    public List<Point> generate(double x1, double y1, double x2, double y2, int steps) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dist = Math.hypot(dx, dy);
        double perpX = -dy / dist;
        double perpY = dx / dist;
        double offset1 = (rnd.nextDouble() * 0.2 + 0.1) * dist * (rnd.nextBoolean() ? 1 : -1);
        double offset2 = (rnd.nextDouble() * 0.2 + 0.1) * dist * (rnd.nextBoolean() ? 1 : -1);

        double cx1 = x1 + dx * 0.33 + perpX * offset1;
        double cy1 = y1 + dy * 0.33 + perpY * offset1;
        double cx2 = x1 + dx * 0.66 + perpX * offset2;
        double cy2 = y1 + dy * 0.66 + perpY * offset2;
        double jitterAmp = Math.min(2.0, dist / 400.0);

        List<Point> path = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double linearT = (double) i / steps;
            double t = 0.5 - 0.5 * Math.cos(Math.PI * linearT);

            double bx = cubicBezier(x1, cx1, cx2, x2, t);
            double by = cubicBezier(y1, cy1, cy2, y2, t);

            if (i > 0 && i < steps) {
                double jitter = rnd.nextGaussian() * jitterAmp;
                bx += perpX * jitter;
                by += perpY * jitter;
            }

            path.add(new Point(bx, by));
        }
        return path;
    }

    private static double cubicBezier(double p0, double p1, double p2, double p3, double t) {
        double u = 1 - t;
        return u * u * u * p0
                + 3 * u * u * t * p1
                + 3 * u * t * t * p2
                + t * t * t * p3;
    }
}