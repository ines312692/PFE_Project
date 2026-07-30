package com.elyadata.feature.scraper.antibot.captcha.detection;

import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmFallbackCaptchaDetector")
class LlmFallbackCaptchaDetectorTest {

    @Mock
    VisionLlmClient visionClient;

    LlmFallbackCaptchaDetector detector;

    private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Method mapTypeMethod;
    private static Method toDoubleMethod;

    @BeforeAll
    static void openReflection() throws Exception {
        mapTypeMethod = LlmFallbackCaptchaDetector.class
                .getDeclaredMethod("mapType", String.class);
        mapTypeMethod.setAccessible(true);

        toDoubleMethod = LlmFallbackCaptchaDetector.class
                .getDeclaredMethod("toDouble", Object.class);
        toDoubleMethod.setAccessible(true);
    }

    @BeforeEach
    void setUp() throws Exception {
        detector = new LlmFallbackCaptchaDetector(visionClient);
        setEnabled(true);
        setMinConfidence(0.7);
    }

    private void setEnabled(boolean value) throws Exception {
        Field f = LlmFallbackCaptchaDetector.class.getDeclaredField("enabled");
        f.setAccessible(true);
        f.set(detector, value);
    }

    private void setMinConfidence(double value) throws Exception {
        Field f = LlmFallbackCaptchaDetector.class.getDeclaredField("minConfidence");
        f.setAccessible(true);
        f.set(detector, value);
    }

    private CaptchaType mapType(String name) throws Exception {
        return (CaptchaType) mapTypeMethod.invoke(null, name);
    }

    private double toDouble(Object v) throws Exception {
        return (double) toDoubleMethod.invoke(null, v);
    }

    @Nested
    @DisplayName("detect() — early-exit paths")
    class DetectEarlyExit {

        @Test
        @DisplayName("returns empty when disabled")
        void disabled_returnsEmpty() throws Exception {
            setEnabled(false);

            Optional<?> result = detector.detect(null, JOB_ID);

            assertThat(result).isEmpty();
            verify(visionClient, never()).isAvailable();
        }

        @Test
        @DisplayName("returns empty when vision client is not available")
        void visionClientUnavailable_returnsEmpty() {
            when(visionClient.isAvailable()).thenReturn(false);

            Optional<?> result = detector.detect(null, JOB_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when snapshot is null (no page)")
        void nullSnapshot_returnsEmpty() {
            when(visionClient.isAvailable()).thenReturn(true);

            Optional<?> result = detector.detect(null, JOB_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when browser context is null")
        void nullBrowserContext_returnsEmpty() {
            when(visionClient.isAvailable()).thenReturn(true);
            PageSnapshot snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .browserContext(null)
                    .build();

            Optional<?> result = detector.detect(snapshot, JOB_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when context has only Selenium driver (no Playwright page)")
        void seleniumOnlyContext_returnsEmpty() {
            when(visionClient.isAvailable()).thenReturn(true);
            WebDriver mockDriver = mock(WebDriver.class);
            LiveBrowserContext ctx = LiveBrowserContext.builder().driver(mockDriver).build();
            PageSnapshot snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .browserContext(ctx)
                    .build();

            Optional<?> result = detector.detect(snapshot, JOB_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("mapType() — static helper")
    class MapType {

        @Test
        @DisplayName("maps RECAPTCHA_V2 correctly")
        void recaptchaV2_mapped() throws Exception {
            assertThat(mapType("RECAPTCHA_V2")).isEqualTo(CaptchaType.RECAPTCHA_V2);
        }

        @Test
        @DisplayName("maps HCAPTCHA correctly")
        void hcaptcha_mapped() throws Exception {
            assertThat(mapType("HCAPTCHA")).isEqualTo(CaptchaType.HCAPTCHA);
        }

        @Test
        @DisplayName("maps TURNSTILE correctly")
        void turnstile_mapped() throws Exception {
            assertThat(mapType("TURNSTILE")).isEqualTo(CaptchaType.TURNSTILE);
        }

        @Test
        @DisplayName("maps CLOUDFLARE_INTERSTITIAL correctly")
        void cloudflareInterstitial_mapped() throws Exception {
            assertThat(mapType("CLOUDFLARE_INTERSTITIAL")).isEqualTo(CaptchaType.CLOUDFLARE_INTERSTITIAL);
        }

        @Test
        @DisplayName("returns null for null input")
        void nullInput_returnsNull() throws Exception {
            assertThat(mapType(null)).isNull();
        }

        @Test
        @DisplayName("returns null for unknown type name")
        void unknownType_returnsNull() throws Exception {
            assertThat(mapType("TOTALLY_UNKNOWN")).isNull();
        }

        @Test
        @DisplayName("lowercased type name is normalized before lookup")
        void lowercaseInput_isNormalized() throws Exception {
            assertThat(mapType("recaptcha_v2")).isEqualTo(CaptchaType.RECAPTCHA_V2);
        }

        @Test
        @DisplayName("leading/trailing whitespace is trimmed before lookup")
        void whitespaceIsStripped() throws Exception {
            assertThat(mapType("  GEETEST  ")).isEqualTo(CaptchaType.GEETEST);
        }
    }

    @Nested
    @DisplayName("toDouble() — static helper")
    class ToDouble {

        @Test
        @DisplayName("converts Integer to double")
        void integer_converted() throws Exception {
            assertThat(toDouble(1)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("converts Double to double")
        void doubleValue_returned() throws Exception {
            assertThat(toDouble(0.85)).isEqualTo(0.85);
        }

        @Test
        @DisplayName("converts string '0.7' to double")
        void stringValue_parsed() throws Exception {
            assertThat(toDouble("0.7")).isEqualTo(0.7);
        }

        @Test
        @DisplayName("returns 0.0 for null input")
        void nullInput_returnsZero() throws Exception {
            assertThat(toDouble(null)).isEqualTo(0.0);
        }
    }
}
