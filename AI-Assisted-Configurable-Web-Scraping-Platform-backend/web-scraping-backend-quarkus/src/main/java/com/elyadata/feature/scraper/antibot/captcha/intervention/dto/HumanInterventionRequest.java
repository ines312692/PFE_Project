package com.elyadata.feature.scraper.antibot.captcha.intervention.dto;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.microsoft.playwright.Page;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


@Getter
@Builder
public class HumanInterventionRequest {

    private final UUID jobId;
    private final String pageUrl;
    private final CaptchaType captchaType;
    private final String siteKey;
    private final String screenshotBase64;
    private final Instant requestedAt;
    private final Instant expiresAt;
    private final Page playwrightPage;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean manuallyConfirmed = new AtomicBoolean(false);

    @Builder.Default
    private final CountDownLatch latch = new CountDownLatch(1);

    @Builder.Default
    private final AtomicReference<String> submittedToken = new AtomicReference<>();


    public void resolveWithToken(String token) {
        submittedToken.set(token);
        latch.countDown();
    }

    public void cancel() {
        submittedToken.set(null);
        latch.countDown();
    }
    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isManuallyConfirmed() {
        return manuallyConfirmed.get();
    }

    public void markManuallyConfirmed() {
        manuallyConfirmed.set(true);
    }

    public String token() {
        return submittedToken.get();
    }
}