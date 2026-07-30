package com.elyadata.feature.scraper.browser.humanizer.motion;

import com.elyadata.feature.scraper.browser.humanizer.timing.HumanTiming;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class BezierMouseMotion {

    private final MousePathGenerator pathGenerator;
    private final HumanTiming timing;


    private static final ThreadLocal<double[]> CURSOR_POS = ThreadLocal.withInitial(() -> new double[]{0, 0});

    public void moveTo(Page page, double targetX, double targetY, double targetWidth) {
        double[] pos = CURSOR_POS.get();
        double startX = pos[0];
        double startY = pos[1];

        if (startX == 0 && startY == 0) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            startX = rnd.nextDouble(200, 1200);
            startY = rnd.nextDouble(100, 600);
        }

        double distance = Math.hypot(targetX - startX, targetY - startY);
        if (distance < 2) {
            return;
        }

        int steps = (int) Math.clamp(distance / 12.0, 15.0, 80.0);
        List<MousePathGenerator.Point> path =
                pathGenerator.generate(startX, startY, targetX, targetY, steps);
        double indexOfDifficulty = Math.log(distance / Math.max(targetWidth, 4) + 1) / Math.log(2);
        long totalMs = (long) (100 + 180 * indexOfDifficulty);
        long perStepMs = Math.max(2, totalMs / steps);

        try {
            for (MousePathGenerator.Point p : path) {
                page.mouse().move(p.x(), p.y());
                long jittered = perStepMs + ThreadLocalRandom.current().nextLong(-1, 3);
                timing.sleep(Math.max(1, jittered));
            }
            pos[0] = targetX;
            pos[1] = targetY;
        } catch (Exception e) {
            log.debug("[HUMAN-MOUSE] mouse move interrupted at ({},{}): {}",
                    targetX, targetY, e.getMessage());
        }
    }

    public void idleMicroMovements(Page page, int wiggleCount) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double[] pos = CURSOR_POS.get();
        double cx = pos[0] != 0 ? pos[0] : rnd.nextDouble(400, 900);
        double cy = pos[1] != 0 ? pos[1] : rnd.nextDouble(300, 600);

        for (int i = 0; i < wiggleCount; i++) {
            double targetX = cx + rnd.nextGaussian() * 50;
            double targetY = cy + rnd.nextGaussian() * 30;
            moveTo(page, targetX, targetY, 30);
            timing.sleep(timing.logNormalDelayMs(400, 0.4));
        }
    }


    public void resetCursor() {
        CURSOR_POS.remove();
    }


}