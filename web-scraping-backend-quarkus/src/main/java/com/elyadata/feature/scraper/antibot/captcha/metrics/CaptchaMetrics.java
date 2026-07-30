package com.elyadata.feature.scraper.antibot.captcha.metrics;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaProvider;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CaptchaMetrics {

    @Inject
    MeterRegistry registry;

    public void recordAttempt(CaptchaType type, CaptchaProvider provider) {
        Counter.builder("captcha.solve.attempts")
                .tag("type", type.name())
                .tag("provider", provider.name())
                .register(registry).increment();
    }

    public void recordSuccess(CaptchaType type, CaptchaProvider provider) {
        Counter.builder("captcha.solve.success")
                .tag("type", type.name())
                .tag("provider", provider.name())
                .register(registry).increment();
    }

    public void recordFailure(CaptchaType type, CaptchaProvider provider, String reason) {
        Counter.builder("captcha.solve.failure")
                .tag("type", type.name())
                .tag("provider", provider.name())
                .tag("reason", reason)
                .register(registry).increment();
    }

    public void recordLatency(CaptchaType type, CaptchaProvider provider, long ms) {
        Timer.builder("captcha.solve.latency")
                .tag("type", type.name())
                .tag("provider", provider.name())
                .register(registry).record(ms, TimeUnit.MILLISECONDS);
    }
}