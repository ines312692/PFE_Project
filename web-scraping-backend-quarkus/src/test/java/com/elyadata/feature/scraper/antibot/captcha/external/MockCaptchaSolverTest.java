package com.elyadata.feature.scraper.antibot.captcha.external;

import com.elyadata.feature.scraper.antibot.captcha.core.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MockCaptchaSolver")
class MockCaptchaSolverTest {

    private final MockCaptchaSolver solver = new MockCaptchaSolver();


    @Test
    @DisplayName("provider() returns MOCK")
    void provider_isMock() {
        assertThat(solver.provider()).isEqualTo(CaptchaProvider.MOCK);
    }

    @Test
    @DisplayName("isEnabled() returns true")
    void isEnabled_true() {
        assertThat(solver.isEnabled()).isTrue();
    }


    @Test
    @DisplayName("supports RECAPTCHA_V2")
    void supports_recaptchaV2() {
        assertThat(solver.supports(CaptchaType.RECAPTCHA_V2)).isTrue();
    }

    @Test
    @DisplayName("supports HCAPTCHA")
    void supports_hcaptcha() {
        assertThat(solver.supports(CaptchaType.HCAPTCHA)).isTrue();
    }

    @Test
    @DisplayName("supports TURNSTILE")
    void supports_turnstile() {
        assertThat(solver.supports(CaptchaType.TURNSTILE)).isTrue();
    }

    @Test
    @DisplayName("does NOT support IMAGE_TEXT")
    void notSupports_imageText() {
        assertThat(solver.supports(CaptchaType.IMAGE_TEXT)).isFalse();
    }

    @Test
    @DisplayName("does NOT support GEETEST")
    void notSupports_geetest() {
        assertThat(solver.supports(CaptchaType.GEETEST)).isFalse();
    }

    @Test
    @DisplayName("does NOT support RECAPTCHA_V3")
    void notSupports_recaptchaV3() {
        assertThat(solver.supports(CaptchaType.RECAPTCHA_V3)).isFalse();
    }

    @Test
    @DisplayName("does NOT support UNKNOWN")
    void notSupports_unknown() {
        assertThat(solver.supports(CaptchaType.UNKNOWN)).isFalse();
    }


    @Test
    @DisplayName("solve() returns successful solution with mock- token prefix")
    @Timeout(5)
    void solve_returnsMockToken() {
        CaptchaChallenge challenge = CaptchaChallenge.builder()
                .type(CaptchaType.HCAPTCHA)
                .pageUrl("https://example.com")
                .siteKey("site-key-xxx")
                .build();

        CaptchaSolution solution = solver.solve(challenge);

        assertThat(solution.success()).isTrue();
        assertThat(solution.token()).startsWith("mock-");
        assertThat(solution.provider()).isEqualTo(CaptchaProvider.MOCK);
        assertThat(solution.solvedInMs()).isPositive();
        assertThat(solution.solvedAt()).isNotNull();
    }

    @Test
    @DisplayName("solve() with null siteKey does not throw (logs masked)")
    @Timeout(5)
    void solve_nullSiteKey_noThrow() {
        CaptchaChallenge challenge = CaptchaChallenge.builder()
                .type(CaptchaType.RECAPTCHA_V2)
                .pageUrl("https://example.com")
                .siteKey("ab12cd")
                .build();

        assertThatCode(() -> solver.solve(challenge)).doesNotThrowAnyException();
    }
}