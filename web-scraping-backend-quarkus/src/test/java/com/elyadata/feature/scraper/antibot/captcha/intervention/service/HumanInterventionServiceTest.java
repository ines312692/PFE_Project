package com.elyadata.feature.scraper.antibot.captcha.intervention.service;

import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.antibot.captcha.orchestration.CaptchaTokenInjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HumanInterventionServiceTest {

    @Mock
    NotificationService notificationService;

    @Mock
    JobPort jobPort;

    @Mock
    CaptchaTokenInjector tokenInjector;

    @InjectMocks
    HumanInterventionService service;

    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setEnabled() throws Exception {
        Field f = HumanInterventionService.class.getDeclaredField("enabled");
        f.setAccessible(true);
        f.set(service, true);
        Field t = HumanInterventionService.class.getDeclaredField("timeoutMinutes");
        t.setAccessible(true);
        t.set(service, 10);
    }

    @Nested
    @DisplayName("supports()")
    class Supports {

        @Test
        @DisplayName("returns true for RECAPTCHA_V2 when enabled")
        void recaptchaV2_supported() {
            assertThat(service.supports(CaptchaType.RECAPTCHA_V2)).isTrue();
        }

        @Test
        @DisplayName("returns true for HCAPTCHA when enabled")
        void hcaptcha_supported() {
            assertThat(service.supports(CaptchaType.HCAPTCHA)).isTrue();
        }

        @Test
        @DisplayName("returns true for TURNSTILE when enabled")
        void turnstile_supported() {
            assertThat(service.supports(CaptchaType.TURNSTILE)).isTrue();
        }

        @Test
        @DisplayName("returns true for CLOUDFLARE_INTERSTITIAL when enabled")
        void cloudflareInterstitial_supported() {
            assertThat(service.supports(CaptchaType.CLOUDFLARE_INTERSTITIAL)).isTrue();
        }

        @Test
        @DisplayName("returns false for GEETEST (unsupported type)")
        void geetest_notSupported() {
            assertThat(service.supports(CaptchaType.GEETEST)).isFalse();
        }

        @Test
        @DisplayName("returns false for IMAGE_TEXT (unsupported type)")
        void imageText_notSupported() {
            assertThat(service.supports(CaptchaType.IMAGE_TEXT)).isFalse();
        }

        @Test
        @DisplayName("returns false for all types when disabled")
        void disabled_returnsFalseForAll() throws Exception {
            Field f = HumanInterventionService.class.getDeclaredField("enabled");
            f.setAccessible(true);
            f.set(service, false);

            assertThat(service.supports(CaptchaType.RECAPTCHA_V2)).isFalse();
            assertThat(service.supports(CaptchaType.HCAPTCHA)).isFalse();
            assertThat(service.supports(CaptchaType.CLOUDFLARE_INTERSTITIAL)).isFalse();
        }
    }

    @Nested
    @DisplayName("findPending()")
    class FindPending {

        @Test
        @DisplayName("returns empty when no intervention is registered")
        void noPending_returnsEmpty() {
            Optional<com.elyadata.feature.scraper.antibot.captcha.intervention.dto.HumanInterventionRequest> result
                    = service.findPending(jobId);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for random unknown jobId")
        void unknownJob_returnsEmpty() {
            assertThat(service.findPending(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("submitToken()")
    class SubmitToken {

        @Test
        @DisplayName("returns false when no pending intervention for job")
        void noPending_returnsFalse() {
            boolean result = service.submitToken(jobId, "some-token");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when token is null")
        void nullToken_returnsFalse() {
            boolean result = service.submitToken(jobId, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when token is blank")
        void blankToken_returnsFalse() {
            boolean result = service.submitToken(jobId, "   ");
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("returns false when no pending intervention for job")
        void noPending_returnsFalse() {
            boolean result = service.cancel(jobId);
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("confirmResolved()")
    class ConfirmResolved {

        @Test
        @DisplayName("returns false when no pending intervention for job")
        void noPending_returnsFalse() {
            boolean result = service.confirmResolved(jobId);
            assertThat(result).isFalse();
        }
    }
}