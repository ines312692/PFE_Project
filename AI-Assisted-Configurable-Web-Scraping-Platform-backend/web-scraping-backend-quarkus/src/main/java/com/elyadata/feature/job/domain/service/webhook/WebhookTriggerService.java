package com.elyadata.feature.job.domain.service.webhook;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.job.domain.service.port.WebhookDeliveryPort;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class WebhookTriggerService {

    /** Max bytes of payload kept in the audit log (avoid bloating the DB). */
    private static final int MAX_PAYLOAD_AUDIT_BYTES = 8 * 1024;

    private final JobPort jobPort;
    private final JobService jobService;
    private final WebhookDeliveryPort deliveryPort;
    private final WebhookSignatureVerifier verifier;
    private final Clock clock;

    public record Outcome(WebhookDeliveryStatus status, int httpStatus, String message) {

        static Outcome accepted() {
            return new Outcome(WebhookDeliveryStatus.ACCEPTED, 202, "Job launch accepted");
        }

        static Outcome unknownToken() {
            return new Outcome(WebhookDeliveryStatus.REJECTED_UNKNOWN_TOKEN, 404, "Webhook not found");
        }

        static Outcome disabled() {
            return new Outcome(WebhookDeliveryStatus.REJECTED_DISABLED, 403, "Webhook disabled");
        }

        static Outcome malformed(String reason) {
            return new Outcome(WebhookDeliveryStatus.REJECTED_MALFORMED_REQUEST, 400, reason);
        }

        static Outcome staleTimestamp() {
            return new Outcome(WebhookDeliveryStatus.REJECTED_STALE_TIMESTAMP, 401, "Timestamp outside tolerance window");
        }

        static Outcome badSignature() {
            return new Outcome(WebhookDeliveryStatus.REJECTED_BAD_SIGNATURE, 401, "Invalid signature");
        }

        static Outcome invalidState(String detail) {
            return new Outcome(WebhookDeliveryStatus.REJECTED_INVALID_STATE, 409, detail);
        }

        static Outcome internal() {
            return new Outcome(WebhookDeliveryStatus.FAILED_INTERNAL, 500, "Internal error");
        }
    }

    public Outcome process(String token,
                           byte[] body,
                           String timestampHdr,
                           String signatureHdr,
                           String sourceIp,
                           String userAgent) {

        long t0 = System.nanoTime();
        Instant receivedAt = Instant.now(clock);

        Outcome outcome;
        UUID jobId = null;
        boolean signatureValid = false;

        try {
            Optional<Job> jobOpt = jobPort.findByWebhookToken(token);
            if (jobOpt.isEmpty()) {
                outcome = Outcome.unknownToken();
            } else {
                Job job = jobOpt.get();
                jobId = job.id();
                outcome = handleResolvedJob(job, body, timestampHdr, signatureHdr);
                signatureValid = (outcome.status() == WebhookDeliveryStatus.ACCEPTED);
            }
        } catch (Exception e) {
            log.error("[WEBHOOK] Unexpected error processing token={}", redact(token), e);
            outcome = Outcome.internal();
        }

        int durationMs = (int) ((System.nanoTime() - t0) / 1_000_000);
        recordDelivery(jobId, receivedAt, outcome, signatureValid, sourceIp, userAgent, body, durationMs);
        return outcome;
    }


    private Outcome handleResolvedJob(Job job, byte[] body, String timestampHdr, String signatureHdr) {
        if (!Boolean.TRUE.equals(job.webhookEnabled())) {
            return Outcome.disabled();
        }
        if (job.webhookSecret() == null || job.webhookSecret().isBlank()) {
            log.warn("[WEBHOOK] Job {} has webhook_enabled=true but no secret — treating as disabled", job.id());
            return Outcome.disabled();
        }

        var verifyResult = verifier.verify(
                job.webhookSecret(), body, timestampHdr, signatureHdr, Instant.now(clock));

        switch (verifyResult) {
            case MISSING_HEADERS -> {
                return Outcome.malformed("Missing X-Webhook-Timestamp or X-Webhook-Signature header");
            }
            case STALE_TIMESTAMP -> {
                return Outcome.staleTimestamp();
            }
            case BAD_SIGNATURE -> {
                return Outcome.badSignature();
            }
            case VALID -> { /* fall through */ }
        }

        try {
            jobService.launchJob(job.id());
            log.info("[WEBHOOK] Triggered job={} via webhook", job.id());
            return Outcome.accepted();
        } catch (DomainException e) {
            log.warn("[WEBHOOK] Job {} cannot be launched: {}", job.id(), e.getMessage());
            return Outcome.invalidState(e.getMessage());
        }
    }

    private void recordDelivery(UUID jobId,
                                Instant receivedAt,
                                Outcome outcome,
                                boolean signatureValid,
                                String sourceIp,
                                String userAgent,
                                byte[] body,
                                int durationMs) {
        if (jobId == null) {
            log.info("[WEBHOOK] Delivery without job: status={} duration={}ms from={}",
                    outcome.status(), durationMs, sourceIp);
            return;
        }

        try {
            deliveryPort.save(WebhookDelivery.builder()
                    .id(UUID.randomUUID())
                    .jobId(jobId)
                    .receivedAt(receivedAt)
                    .status(outcome.status())
                    .httpStatus(outcome.httpStatus())
                    .sourceIp(truncate(sourceIp, 45))
                    .userAgent(truncate(userAgent, 512))
                    .signatureValid(signatureValid)
                    .errorMessage(outcome.status() == WebhookDeliveryStatus.ACCEPTED ? null
                            : truncate(outcome.message(), 512))
                    .payload(truncatePayload(body))
                    .durationMs(durationMs)
                    .build());
        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to persist delivery audit for job={}", jobId, e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String truncatePayload(byte[] body) {
        if (body == null || body.length == 0) return null;
        int len = Math.min(body.length, MAX_PAYLOAD_AUDIT_BYTES);
        return new String(body, 0, len, StandardCharsets.UTF_8);
    }

    private static String redact(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 8) + "…";
    }
}