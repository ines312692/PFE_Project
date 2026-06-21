package com.elyadata.feature.scraper.browser.humanizer.scroll;

import com.elyadata.feature.scraper.browser.humanizer.timing.HumanTiming;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HumanScroll {

    private final HumanTiming timing;


    public void scrollToBottom(Page page, int maxScrolls) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long prevHeight = 0;
        int stagnant = 0;

        for (int i = 0; i < maxScrolls; i++) {
            int delta = (int) Math.max(80, 400 + rnd.nextGaussian() * 150);
            boolean backScroll = rnd.nextInt(10) == 0 && i > 2;
            int signedDelta = backScroll ? -Math.min(delta, 200) : delta;

            page.mouse().wheel(0, signedDelta);

            timing.sleep(timing.logNormalDelayMs(400, 0.5));

            if (rnd.nextInt(7) == 0) {
                timing.sleep(timing.logNormalDelayMs(1500, 0.4));
            }

            long currHeight = ((Number) page.evaluate("() => document.body.scrollHeight")).longValue();
            if (currHeight == prevHeight) {
                stagnant++;
                if (stagnant >= 3) break;
            } else {
                stagnant = 0;
            }
            prevHeight = currHeight;
        }
        log.debug("[HUMAN-SCROLL] Reached bottom or max scrolls");
    }


    public void readingScroll(Page page) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int bursts = rnd.nextInt(3, 6);
        for (int i = 0; i < bursts; i++) {
            int delta = (int) Math.max(150, 350 + rnd.nextGaussian() * 100);
            page.mouse().wheel(0, delta);
            timing.sleep(timing.logNormalDelayMs(600, 0.5));
        }
    }
}