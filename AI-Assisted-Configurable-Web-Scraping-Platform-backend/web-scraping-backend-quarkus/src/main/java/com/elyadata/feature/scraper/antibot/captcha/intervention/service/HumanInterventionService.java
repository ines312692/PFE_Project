package com.elyadata.feature.scraper.antibot.captcha.intervention.service;

import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.orchestration.CaptchaTokenInjector;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.antibot.captcha.intervention.dto.HumanInterventionRequest;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HumanInterventionService {

    private static final Set<CaptchaType> TOKEN_TYPES = Set.of(
            CaptchaType.RECAPTCHA_V2,
            CaptchaType.RECAPTCHA_V3,
            CaptchaType.HCAPTCHA,
            CaptchaType.TURNSTILE,
            CaptchaType.FUNCAPTCHA
    );

    private static final Set<CaptchaType> IN_SESSION_TYPES = Set.of(
            CaptchaType.CLOUDFLARE_INTERSTITIAL
    );

    private static final long INTERSTITIAL_POLL_INTERVAL_MS = 1_500;

    private final ConcurrentHashMap<UUID, HumanInterventionRequest> pending =
            new ConcurrentHashMap<>();

    private final NotificationService notificationService;
    private final JobPort jobPort;
    private final CaptchaTokenInjector tokenInjector;

    @ConfigProperty(name = "captcha.human-intervention.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "captcha.human-intervention.timeout-minutes", defaultValue = "10")
    int timeoutMinutes;

    public boolean supports(CaptchaType type) {
        if (!enabled) return false;
        return TOKEN_TYPES.contains(type) || IN_SESSION_TYPES.contains(type);
    }

    public boolean requestInterventionAndWait(PageSnapshot snapshot,
                                              CaptchaChallenge challenge,
                                              Page page) {
        if (!supports(challenge.type())) {
            log.warn("[HUMAN-INTERVENTION] Type {} not supported for human resolution",
                    challenge.type());
            return false;
        }

        if (isHeadless(page)) {
            log.warn("[HUMAN-INTERVENTION] Browser is headless — a human cannot resolve type={} " +
                            "in an invisible window. Aborting cleanly for job={} url={}. " +
                            "Re-run this job with a visible browser to allow human resolution.",
                    challenge.type(), challenge.jobId(), challenge.pageUrl());
            updateJobStatus(challenge.jobId(), JobStatus.WAITING_FOR_HUMAN);
            sendHeadlessBlockedNotification(challenge.jobId(), challenge);
            return false;
        }

        if (IN_SESSION_TYPES.contains(challenge.type())) {
            return requestInterstitialResolveAndWait(challenge, page);
        }
        return requestTokenInterventionAndWait(snapshot, challenge, page);
    }

    private boolean requestTokenInterventionAndWait(PageSnapshot snapshot,
                                                    CaptchaChallenge challenge,
                                                    Page page) {
        UUID jobId = challenge.jobId();
        if (pending.containsKey(jobId)) {
            log.warn("[HUMAN-INTERVENTION] Intervention already pending for job={}", jobId);
            return false;
        }

        String screenshotB64 = captureScreenshot(page);
        Instant now = Instant.now();
        Duration timeout = Duration.ofMinutes(timeoutMinutes);

        HumanInterventionRequest request = HumanInterventionRequest.builder()
                .jobId(jobId)
                .pageUrl(challenge.pageUrl())
                .captchaType(challenge.type())
                .siteKey(challenge.siteKey())
                .screenshotBase64(screenshotB64)
                .requestedAt(now)
                .expiresAt(now.plus(timeout))
                .playwrightPage(page)
                .build();

        pending.put(jobId, request);
        updateJobStatus(jobId, JobStatus.WAITING_FOR_HUMAN);
        sendNotification(jobId, challenge);

        log.warn("[HUMAN-INTERVENTION] Job={} now WAITING_FOR_HUMAN — type={} timeout={}min url={}",
                jobId, challenge.type(), timeoutMinutes, challenge.pageUrl());

        try {
            boolean signalled = request.getLatch().await(timeoutMinutes, TimeUnit.MINUTES);

            if (!signalled) {
                log.warn("[HUMAN-INTERVENTION] Timeout reached for job={} after {}min — " +
                        "no token submitted", jobId, timeoutMinutes);
                return false;
            }

            String token = request.token();
            if (token == null || token.isBlank()) {
                log.warn("[HUMAN-INTERVENTION] Job={} cancelled by user (no token)", jobId);
                return false;
            }

            boolean injected = tokenInjector.inject(snapshot, challenge.type(), token);
            if (injected) {
                log.info("[HUMAN-INTERVENTION] Job={} token injected successfully — resuming scrape",
                        jobId);
                updateJobStatus(jobId, JobStatus.RUNNING);
                return true;
            }

            log.warn("[HUMAN-INTERVENTION] Job={} token injection failed", jobId);
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[HUMAN-INTERVENTION] Job={} interrupted while waiting", jobId);
            return false;
        } finally {
            pending.remove(jobId);
        }
    }

    private boolean requestInterstitialResolveAndWait(CaptchaChallenge challenge, Page page) {
        UUID jobId = challenge.jobId();
        if (pending.containsKey(jobId)) {
            log.warn("[HUMAN-INTERVENTION] Intervention already pending for job={}", jobId);
            return false;
        }

        Instant now = Instant.now();
        Duration timeout = Duration.ofMinutes(timeoutMinutes);

        HumanInterventionRequest request = HumanInterventionRequest.builder()
                .jobId(jobId)
                .pageUrl(challenge.pageUrl())
                .captchaType(challenge.type())
                .siteKey(challenge.siteKey())
                .screenshotBase64(captureScreenshot(page))
                .requestedAt(now)
                .expiresAt(now.plus(timeout))
                .playwrightPage(page)
                .build();

        pending.put(jobId, request);
        updateJobStatus(jobId, JobStatus.WAITING_FOR_HUMAN);
        sendNotification(jobId, challenge);

        log.warn("[HUMAN-INTERVENTION] Job={} WAITING_FOR_HUMAN (in-session interstitial) — " +
                        "human must solve the Cloudflare challenge in the visible browser window. " +
                        "timeout={}min url={}",
                jobId, timeoutMinutes, challenge.pageUrl());

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String beforeUrl = safeUrl(page);

        try {
            while (System.currentTimeMillis() < deadline) {
                if (request.isCancelled()) {
                    log.info("[HUMAN-INTERVENTION] Job={} interstitial cancelled by user", jobId);
                    return false;
                }

                if (request.isManuallyConfirmed()) {
                    boolean cleared = interstitialCleared(page, beforeUrl);
                    log.info("[HUMAN-INTERVENTION] Job={} manual confirmation received " +
                                    "(cf_clearance/redirect detected={}) — resuming scrape",
                            jobId, cleared);
                    updateJobStatus(jobId, JobStatus.RUNNING);
                    return true;
                }

                if (interstitialCleared(page, beforeUrl)) {
                    log.info("[HUMAN-INTERVENTION] Job={} interstitial cleared " +
                            "(cf_clearance/redirect detected) — resuming scrape", jobId);
                    updateJobStatus(jobId, JobStatus.RUNNING);
                    return true;
                }

                sleep();
            }
            log.warn("[HUMAN-INTERVENTION] Timeout reached for job={} after {}min — " +
                    "interstitial not cleared", jobId, timeoutMinutes);
            return false;
        } finally {
            pending.remove(jobId);
        }
    }

    public boolean submitToken(UUID jobId, String token) {
        HumanInterventionRequest request = pending.get(jobId);
        if (request == null) {
            log.warn("[HUMAN-INTERVENTION] No pending intervention for job={}", jobId);
            return false;
        }
        if (request.getCaptchaType() == CaptchaType.CLOUDFLARE_INTERSTITIAL) {
            log.warn("[HUMAN-INTERVENTION] job={} is an in-session interstitial — use /confirm, " +
                    "not /submit", jobId);
            return false;
        }
        if (token == null || token.isBlank()) {
            log.warn("[HUMAN-INTERVENTION] Empty token submitted for job={}", jobId);
            return false;
        }
        log.info("[HUMAN-INTERVENTION] Token received for job={} (len={})", jobId, token.length());
        request.resolveWithToken(token);
        return true;
    }

    public boolean confirmResolved(UUID jobId) {
        HumanInterventionRequest request = pending.get(jobId);
        if (request == null) {
            log.warn("[HUMAN-INTERVENTION] No pending intervention for job={}", jobId);
            return false;
        }
        if (request.getCaptchaType() != CaptchaType.CLOUDFLARE_INTERSTITIAL) {
            log.warn("[HUMAN-INTERVENTION] job={} is token-based — use /submit, not /confirm", jobId);
            return false;
        }
        log.info("[HUMAN-INTERVENTION] Manual confirmation received for job={}", jobId);
        request.markManuallyConfirmed();
        return true;
    }

    public boolean cancel(UUID jobId) {
        HumanInterventionRequest request = pending.get(jobId);
        if (request == null) return false;
        log.info("[HUMAN-INTERVENTION] Cancellation received for job={}", jobId);
        request.cancel();
        return true;
    }

    public Optional<HumanInterventionRequest> findPending(UUID jobId) {
        return Optional.ofNullable(pending.get(jobId));
    }

    private static boolean interstitialCleared(Page page, String beforeUrl) {
        return hasCfClearance(page) || leftChallengePage(page, beforeUrl);
    }

    private static boolean isHeadless(Page page) {
        try {
            Object r = page.evaluate("""
                    () => {
                      const ua = navigator.userAgent || '';
                      if (/headless/i.test(ua)) return true;
                      if (window.outerWidth === 0 || window.outerHeight === 0) return true;
                      return false;
                    }
                    """);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasCfClearance(Page page) {
        try {
            return page.context().cookies().stream()
                    .anyMatch(c -> "cf_clearance".equals(c.name)
                            && c.value != null && !c.value.isBlank());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean leftChallengePage(Page page, String beforeUrl) {
        String now = safeUrl(page);
        if (now == null) return false;
        boolean stillOnChallenge = now.contains("/cdn-cgi/challenge-platform/")
                || now.contains("__cf_chl_")
                || now.contains("__cf_chl_tk");
        if (stillOnChallenge) return false;
        boolean stillCfWidget = cfWidgetPresent(page);
        if (stillCfWidget) return false;
        return !now.equals(beforeUrl);
    }

    private static boolean cfWidgetPresent(Page page) {
        try {
            Object r = page.evaluate("""
                    () => !!document.querySelector(
                      'iframe[src*="challenges.cloudflare.com"], #challenge-stage, ' +
                      '#challenge-form, #challenge-running'
                    )
                    """);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (Exception e) {
            return null;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(HumanInterventionService.INTERSTITIAL_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String captureScreenshot(Page page) {
        try {
            byte[] bytes = page.screenshot();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("[HUMAN-INTERVENTION] Screenshot failed: {}", e.getMessage());
            return "";
        }
    }

    private void updateJobStatus(UUID jobId, JobStatus newStatus) {
        try {
            jobPort.findById(jobId).ifPresent(j ->
                    jobPort.update(j.toBuilder().status(newStatus).build()));
        } catch (Exception e) {
            log.warn("[HUMAN-INTERVENTION] Failed to update job status: {}", e.getMessage());
        }
    }

    private void sendNotification(UUID jobId, CaptchaChallenge challenge) {
        try {
            Job job = jobPort.findById(jobId).orElse(null);
            if (job == null || job.createdBy() == null) return;
            String body = challenge.type() == CaptchaType.CLOUDFLARE_INTERSTITIAL
                    ? String.format("Job '%s' is blocked by a Cloudflare challenge on %s. " +
                            "Solve it in the open browser window; the scrape resumes automatically.",
                    job.name(), challenge.pageUrl())
                    : String.format("Job '%s' is blocked by a %s captcha on %s. " +
                            "Open the URL, solve the captcha, and submit the token.",
                    job.name(), challenge.type(), challenge.pageUrl());
            notificationService.notify(
                    job.createdBy(),
                    jobId,
                    NotificationType.CAPTCHA_INTERVENTION_REQUIRED,
                    "Captcha intervention required: " + job.name(),
                    body);
        } catch (Exception e) {
            log.warn("[HUMAN-INTERVENTION] Notification failed for job={}: {}", jobId, e.getMessage());
        }
    }

    private void sendHeadlessBlockedNotification(UUID jobId, CaptchaChallenge challenge) {
        try {
            Job job = jobPort.findById(jobId).orElse(null);
            if (job == null || job.createdBy() == null) return;
            notificationService.notify(
                    job.createdBy(),
                    jobId,
                    NotificationType.CAPTCHA_INTERVENTION_REQUIRED,
                    "Captcha needs a visible browser: " + job.name(),
                    String.format("Job '%s' hit a %s challenge on %s while running headless. " +
                                    "Re-run with a visible browser to resolve it manually.",
                            job.name(), challenge.type(), challenge.pageUrl()));
        } catch (Exception e) {
            log.warn("[HUMAN-INTERVENTION] Headless-blocked notification failed for job={}: {}",
                    jobId, e.getMessage());
        }
    }
}