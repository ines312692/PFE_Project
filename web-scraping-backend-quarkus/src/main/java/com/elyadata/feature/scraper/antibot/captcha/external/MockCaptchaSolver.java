package com.elyadata.feature.scraper.antibot.captcha.external;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaProvider;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolution;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolver;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@ApplicationScoped
public class MockCaptchaSolver implements CaptchaSolver {

    private static final Set<CaptchaType> SUPPORTED_TYPES = Set.of(
            CaptchaType.RECAPTCHA_V2,
            CaptchaType.HCAPTCHA,
            CaptchaType.TURNSTILE
    );

    private static final int MIN_LATENCY_MS = 500;
    private static final int MAX_LATENCY_MS = 2_000;
    private static final String TOKEN_PREFIX = "mock-";

    @Override
    public CaptchaProvider provider() {
        return CaptchaProvider.MOCK;
    }

    @Override
    public boolean supports(CaptchaType type) {
        return SUPPORTED_TYPES.contains(type);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public CaptchaSolution solve(CaptchaChallenge challenge) {
        long startMs = System.currentTimeMillis();
        long simulatedLatency = ThreadLocalRandom.current().nextLong(MIN_LATENCY_MS, MAX_LATENCY_MS);

        log.info("[CAPTCHA-MOCK] Simulating solve type={} siteKey={} latency={}ms job={}",
                challenge.type(), maskSiteKey(challenge.siteKey()), simulatedLatency, challenge.jobId());

        sleep(simulatedLatency);

        String token = TOKEN_PREFIX + UUID.randomUUID();
        long elapsed = System.currentTimeMillis() - startMs;

        log.info("[CAPTCHA-MOCK] Solved type={} in {}ms (token={}...)",
                challenge.type(), elapsed, token.substring(0, Math.min(15, token.length())));

        return CaptchaSolution.success(token, CaptchaProvider.MOCK, elapsed, 0.0);
    }

    private static String maskSiteKey(String siteKey) {
        if (siteKey == null || siteKey.length() < 8) return "***";
        return siteKey.substring(0, 4) + "..." + siteKey.substring(siteKey.length() - 4);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}