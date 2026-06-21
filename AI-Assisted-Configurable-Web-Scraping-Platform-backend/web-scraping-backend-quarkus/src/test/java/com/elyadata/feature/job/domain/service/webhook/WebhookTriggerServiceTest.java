package com.elyadata.feature.job.domain.service.webhook;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.job.domain.service.port.WebhookDeliveryPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookTriggerService")
class WebhookTriggerServiceTest {

    @Mock
    JobPort jobPort;
    @Mock
    JobService jobService;
    @Mock
    WebhookDeliveryPort deliveryPort;
    @Mock
    WebhookSignatureVerifier verifier;

    private WebhookTriggerService service;

    private static final Instant FIXED_NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final String TOKEN = "valid-token-abc";
    private static final byte[] BODY = "{}".getBytes();
    private static final String TS = String.valueOf(FIXED_NOW.getEpochSecond());
    private static final String SIG = "sha256=somesig";

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        service = new WebhookTriggerService(jobPort, jobService, deliveryPort, verifier, fixed);
    }

    private Job webhookJob(boolean enabled, String secret) {
        return Job.builder()
                .id(JOB_ID)
                .name("test-job")
                .targetUrl("https://example.com")
                .jobType(JobType.SCRAPER)
                .status(JobStatus.CREATED)
                .priority(JobPriority.NORMAL)
                .triggerType(JobTriggerType.WEBHOOK)
                .extractionMode(ExtractionMode.CSS_SELECTOR)
                .webhookEnabled(enabled)
                .webhookToken(TOKEN)
                .webhookSecret(secret)
                .build();
    }


    @Nested
    @DisplayName("ACCEPTED outcomes")
    class AcceptedOutcomes {

        @Test
        @DisplayName("returns 202 ACCEPTED for valid token, enabled webhook, valid signature")
        void validRequestAccepted() {
            Job job = webhookJob(true, "my-secret");
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));
            when(verifier.verify(any(), any(), any(), any(), any()))
                    .thenReturn(WebhookSignatureVerifier.Result.VALID);

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, "1.2.3.4", "curl/7");

            assertThat(outcome.httpStatus()).isEqualTo(202);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.ACCEPTED);
            verify(jobService).launchJob(JOB_ID);
            verify(deliveryPort).save(any(WebhookDelivery.class));
        }
    }


    @Nested
    @DisplayName("rejection outcomes")
    class RejectionOutcomes {

        @Test
        @DisplayName("returns 404 when token not found")
        void unknownToken() {
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.empty());

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(404);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.REJECTED_UNKNOWN_TOKEN);
            verify(jobService, never()).launchJob(any());
        }

        @Test
        @DisplayName("returns 403 when webhook is disabled")
        void webhookDisabled() {
            Job job = webhookJob(false, "secret");
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(403);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.REJECTED_DISABLED);
        }

        @Test
        @DisplayName("returns 403 when webhook enabled but no secret configured")
        void enabledButNoSecret() {
            Job job = webhookJob(true, null);
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(403);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.REJECTED_DISABLED);
        }

        @Test
        @DisplayName("returns 400 when signature headers are missing")
        void missingHeaders() {
            Job job = webhookJob(true, "secret");
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));
            when(verifier.verify(any(), any(), any(), any(), any()))
                    .thenReturn(WebhookSignatureVerifier.Result.MISSING_HEADERS);

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(400);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.REJECTED_MALFORMED_REQUEST);
        }

        @Test
        @DisplayName("returns 401 when timestamp is stale")
        void staleTimestamp() {
            Job job = webhookJob(true, "secret");
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));
            when(verifier.verify(any(), any(), any(), any(), any()))
                    .thenReturn(WebhookSignatureVerifier.Result.STALE_TIMESTAMP);

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(401);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.REJECTED_STALE_TIMESTAMP);
        }

        @Test
        @DisplayName("returns 401 when signature is invalid")
        void badSignature() {
            Job job = webhookJob(true, "secret");
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));
            when(verifier.verify(any(), any(), any(), any(), any()))
                    .thenReturn(WebhookSignatureVerifier.Result.BAD_SIGNATURE);

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(401);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.REJECTED_BAD_SIGNATURE);
        }

        @Test
        @DisplayName("returns 409 when job cannot be launched from its current state")
        void jobInvalidState() {
            Job job = webhookJob(true, "secret");
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));
            when(verifier.verify(any(), any(), any(), any(), any()))
                    .thenReturn(WebhookSignatureVerifier.Result.VALID);
            doThrow(DomainException.notValid("already RUNNING"))
                    .when(jobService).launchJob(JOB_ID);

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(409);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.REJECTED_INVALID_STATE);
        }
    }


    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("returns 500 when an unexpected exception is thrown")
        void internalErrorHandled() {
            when(jobPort.findByWebhookToken(TOKEN))
                    .thenThrow(new RuntimeException("db down"));

            WebhookTriggerService.Outcome outcome =
                    service.process(TOKEN, BODY, TS, SIG, null, null);

            assertThat(outcome.httpStatus()).isEqualTo(500);
            assertThat(outcome.status()).isEqualTo(WebhookDeliveryStatus.FAILED_INTERNAL);
        }

        @Test
        @DisplayName("delivery is persisted even on rejection")
        void deliveryAlwaysPersisted() {
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.empty());

            service.process(TOKEN, BODY, TS, SIG, "10.0.0.1", "agent");

            // For unknown token, jobId is null — delivery is only logged, not persisted
            verify(deliveryPort, never()).save(any());
        }

        @Test
        @DisplayName("delivery is persisted for known job rejections")
        void deliveryPersistedForKnownJob() {
            Job job = webhookJob(false, "secret");
            when(jobPort.findByWebhookToken(TOKEN)).thenReturn(Optional.of(job));

            service.process(TOKEN, BODY, TS, SIG, "10.0.0.1", "agent");

            verify(deliveryPort).save(argThat(d ->
                    d.jobId().equals(JOB_ID) &&
                            d.status() == WebhookDeliveryStatus.REJECTED_DISABLED));
        }
    }


    @Nested
    @DisplayName("Outcome factory methods")
    class OutcomeFactories {

        @Test
        @DisplayName("accepted() → 202 ACCEPTED")
        void accepted() {
            var o = WebhookTriggerService.Outcome.accepted();
            assertThat(o.httpStatus()).isEqualTo(202);
            assertThat(o.status()).isEqualTo(WebhookDeliveryStatus.ACCEPTED);
        }

        @Test
        @DisplayName("unknownToken() → 404")
        void unknownToken() {
            assertThat(WebhookTriggerService.Outcome.unknownToken().httpStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("disabled() → 403")
        void disabled() {
            assertThat(WebhookTriggerService.Outcome.disabled().httpStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("internal() → 500")
        void internal() {
            assertThat(WebhookTriggerService.Outcome.internal().httpStatus()).isEqualTo(500);
        }

        @Test
        @DisplayName("malformed(reason) → 400 with the given message")
        void malformed() {
            var o = WebhookTriggerService.Outcome.malformed("bad header");
            assertThat(o.httpStatus()).isEqualTo(400);
            assertThat(o.message()).contains("bad header");
        }
    }
}
