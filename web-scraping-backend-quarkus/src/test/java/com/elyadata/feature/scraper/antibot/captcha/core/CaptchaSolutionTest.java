package com.elyadata.feature.scraper.antibot.captcha.core;

import org.junit.jupiter.api.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CaptchaSolution")
class CaptchaSolutionTest {

    @Test
    @DisplayName("success() sets success=true with token, provider, timing and cost")
    void success_fields() {
        CaptchaSolution s = CaptchaSolution.success("tok123", CaptchaProvider.MOCK, 1200L, 0.0);

        assertThat(s.success()).isTrue();
        assertThat(s.token()).isEqualTo("tok123");
        assertThat(s.provider()).isEqualTo(CaptchaProvider.MOCK);
        assertThat(s.solvedInMs()).isEqualTo(1200L);
        assertThat(s.costUsd()).isEqualTo(0.0);
        assertThat(s.errorMessage()).isNull();
        assertThat(s.solvedAt()).isNotNull();
    }

    @Test
    @DisplayName("success() sets solvedAt to approximately now")
    void success_solvedAtIsNow() {
        Instant before = Instant.now();
        CaptchaSolution s = CaptchaSolution.success("tok", CaptchaProvider.MOCK, 500L, 0.0);
        Instant after = Instant.now();

        assertThat(s.solvedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("failure() sets success=false with error message, no token")
    void failure_fields() {
        CaptchaSolution s = CaptchaSolution.failure(CaptchaProvider.MOCK, 900L, "timeout");

        assertThat(s.success()).isFalse();
        assertThat(s.token()).isNull();
        assertThat(s.provider()).isEqualTo(CaptchaProvider.MOCK);
        assertThat(s.solvedInMs()).isEqualTo(900L);
        assertThat(s.errorMessage()).isEqualTo("timeout");
        assertThat(s.solvedAt()).isNotNull();
    }

    @Test
    @DisplayName("failure() sets solvedAt to approximately now")
    void failure_solvedAtIsNow() {
        Instant before = Instant.now();
        CaptchaSolution s = CaptchaSolution.failure(CaptchaProvider.MOCK, 0L, "err");
        Instant after = Instant.now();

        assertThat(s.solvedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("success() with non-zero cost stores the cost")
    void success_nonZeroCost() {
        CaptchaSolution s = CaptchaSolution.success("tok", CaptchaProvider.TWO_CAPTCHA, 500L, 0.003);
        assertThat(s.costUsd()).isEqualTo(0.003);
        assertThat(s.provider()).isEqualTo(CaptchaProvider.TWO_CAPTCHA);
    }

    @Test
    @DisplayName("builder allows direct construction with all fields")
    void builder_directConstruction() {
        CaptchaSolution s = CaptchaSolution.builder()
                .token("t")
                .provider(CaptchaProvider.HUMAN)
                .success(true)
                .solvedInMs(100L)
                .costUsd(0.01)
                .errorMessage(null)
                .solvedAt(Instant.EPOCH)
                .build();

        assertThat(s.token()).isEqualTo("t");
        assertThat(s.solvedAt()).isEqualTo(Instant.EPOCH);
    }
}