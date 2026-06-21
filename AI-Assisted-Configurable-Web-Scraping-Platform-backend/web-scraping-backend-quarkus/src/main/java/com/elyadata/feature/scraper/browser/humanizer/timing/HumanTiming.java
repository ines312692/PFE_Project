package com.elyadata.feature.scraper.browser.humanizer.timing;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ThreadLocalRandom;


@ApplicationScoped
public class HumanTiming {


    public long logNormalDelayMs(long medianMs, double sigma) {
        double mu = Math.log(medianMs);
        double normal = ThreadLocalRandom.current().nextGaussian();
        double sample = Math.exp(mu + sigma * normal);
        return Math.min((long) sample, medianMs * 10);
    }


    public long reactionTimeMs() {
        return logNormalDelayMs(350, 0.5);
    }

    public long readingTimeMs(int visibleTextChars) {
        long base = Math.clamp(visibleTextChars * 15L, 800L, 12_000L);
        return logNormalDelayMs(base, 0.3);
    }

    public long interActionDelayMs() {
        return logNormalDelayMs(1200, 0.6);
    }

    public void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}