package com.elyadata.feature.scraper.antibot.captcha.detection;

import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("LiveCaptchaDetector")
class LiveCaptchaDetectorTest {

    private final LiveCaptchaDetector detector = new LiveCaptchaDetector();
    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Method parseMethod;

    @BeforeAll
    static void openParseMethod() throws Exception {
        parseMethod = LiveCaptchaDetector.class
                .getDeclaredMethod("parse", String.class);
        parseMethod.setAccessible(true);
    }

    private Object parse(String raw) throws Exception {
        return parseMethod.invoke(null, raw);
    }

    @Nested
    @DisplayName("detect() — null/no-browser snapshot cases")
    class DetectNoBrowser {

        @Test
        @DisplayName("returns empty when snapshot is null")
        void nullSnapshot_returnsEmpty() {
            Optional<?> result = detector.detect(null, JOB_ID);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when browserContext is null")
        void nullBrowserContext_returnsEmpty() {
            PageSnapshot snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .browserContext(null)
                    .build();

            Optional<?> result = detector.detect(snapshot, JOB_ID);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when context has only a Selenium driver (no Playwright page)")
        void seleniumOnlyContext_returnsEmpty() {
            WebDriver mockDriver = mock(WebDriver.class);
            LiveBrowserContext seleniumCtx = LiveBrowserContext.builder()
                    .driver(mockDriver)
                    .build();
            PageSnapshot snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .browserContext(seleniumCtx)
                    .build();

            Optional<?> result = detector.detect(snapshot, JOB_ID);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when context has neither page nor driver")
        void emptyContext_returnsEmpty() {
            LiveBrowserContext emptyCtx = LiveBrowserContext.builder().build();
            PageSnapshot snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .browserContext(emptyCtx)
                    .build();

            Optional<?> result = detector.detect(snapshot, JOB_ID);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("parse() — private static method via reflection")
    class ParseMethod {

        @Test
        @DisplayName("returns non-null for valid RECAPTCHA_V2 payload")
        void validRecaptchaV2_returnsNonNull() throws Exception {
            Object result = parse("RECAPTCHA_V2|site-key-abc123");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("returns non-null for valid HCAPTCHA payload")
        void validHcaptcha_returnsNonNull() throws Exception {
            Object result = parse("HCAPTCHA|my-hcaptcha-key");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("returns non-null for valid TURNSTILE payload")
        void validTurnstile_returnsNonNull() throws Exception {
            Object result = parse("TURNSTILE|cf-turnstile-key");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("returns non-null for valid GEETEST payload")
        void validGeetest_returnsNonNull() throws Exception {
            Object result = parse("GEETEST|geetest-no-sitekey");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("returns null when separator is at position 0 (empty type)")
        void separatorAtStart_returnsNull() throws Exception {
            Object result = parse("|site-key");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null when there is no separator character")
        void noSeparator_returnsNull() throws Exception {
            Object result = parse("RECAPTCHA_V2");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null when sitekey is empty after separator")
        void emptySiteKey_returnsNull() throws Exception {
            Object result = parse("RECAPTCHA_V2|");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null when sitekey is blank (whitespace) after separator")
        void blankSiteKey_returnsNull() throws Exception {
            Object result = parse("RECAPTCHA_V2|   ");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for unknown captcha type")
        void unknownType_returnsNull() throws Exception {
            Object result = parse("UNKNOWN_CAPTCHA_TYPE|some-key");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("type name is uppercased before lookup")
        void lowercaseTypeName_isNormalized() throws Exception {
            Object result = parse("recaptcha_v2|site-key");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("sitekey is trimmed of surrounding whitespace")
        void sitekeyWithWhitespace_isTrimmed() throws Exception {
            Object result = parse("TURNSTILE|  trimmed-key  ");
            assertThat(result).isNotNull();
        }
    }
}
