package com.elyadata.feature.scraper.antibot.captcha.solvers.llm;

import com.elyadata.common.llm.LlmUnavailableException;
import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.common.llm.observability.LlmCallContext;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaProvider;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolution;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolverException;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmImageCaptchaSolver")
class LlmImageCaptchaSolverTest {

    @Mock
    VisionLlmClient visionClient;
    @Mock
    CaptchaImagePreprocessor preprocessor;
    @Mock
    CaptchaPromptFactory prompts;

    LlmCallContext context;
    LlmImageCaptchaSolver solver;

    private final byte[] enhancedBytes = "enhanced".getBytes();
    private final String rawImageB64 = Base64.getEncoder().encodeToString("raw-image".getBytes());

    @BeforeEach
    void setUp() throws Exception {
        context = new LlmCallContext();
        solver = new LlmImageCaptchaSolver(visionClient, context, preprocessor, prompts);
        setVotingEnabled(true);
        lenient().when(prompts.classifierPrompt()).thenReturn("classify?");
        lenient().when(prompts.textCaptchaPrompt()).thenReturn("text-prompt");
        lenient().when(prompts.mathCaptchaPrompt()).thenReturn("math-prompt");
    }

    private void setVotingEnabled(boolean value) throws Exception {
        Field f = LlmImageCaptchaSolver.class.getDeclaredField("votingEnabled");
        f.setAccessible(true);
        f.set(solver, value);
    }

    private CaptchaChallenge challenge() {
        return CaptchaChallenge.builder()
                .type(CaptchaType.IMAGE_TEXT)
                .pageUrl("https://example.com")
                .jobId(UUID.randomUUID())
                .imageBase64(rawImageB64)
                .build();
    }

    @Test
    @DisplayName("provider() is LLM_VISION")
    void provider_isLlmVision() {
        assertThat(solver.provider()).isEqualTo(CaptchaProvider.LLM_VISION);
    }

    @Test
    @DisplayName("supports() only IMAGE_TEXT")
    void supports_onlyImageText() {
        assertThat(solver.supports(CaptchaType.IMAGE_TEXT)).isTrue();
        assertThat(solver.supports(CaptchaType.RECAPTCHA_V2)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() delegates to visionClient.isAvailable()")
    void isEnabled_delegatesToVisionClient() {
        when(visionClient.isAvailable()).thenReturn(true);
        assertThat(solver.isEnabled()).isTrue();

        when(visionClient.isAvailable()).thenReturn(false);
        assertThat(solver.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("solve() throws when the vision client is not configured")
    void solve_notEnabled_throwsInvalidConfig() {
        when(visionClient.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> solver.solve(challenge()))
                .isInstanceOf(CaptchaSolverException.class)
                .hasMessageContaining("not configured");
    }

    @Nested
    @DisplayName("solve() — classification + voting")
    class SolveHappyPaths {

        @BeforeEach
        void enable() {
            when(visionClient.isAvailable()).thenReturn(true);
        }

        @Test
        @DisplayName("returns a success solution when 2/3 voting passes agree above the confidence floor")
        void majorityAgreement_returnsSuccess() throws Exception {
            when(preprocessor.enhance(any())).thenReturn(enhancedBytes);
            when(visionClient.analyzeImage(eq("classify?"), anyString(), eq("image/png")))
                    .thenReturn("TEXT");
            when(visionClient.analyzeImage(eq("text-prompt"), anyString(), eq("image/png")))
                    .thenReturn(
                            "{\"answer\":\"abcd\",\"confidence\":0.9}",
                            "{\"answer\":\"abcd\",\"confidence\":0.8}",
                            "{\"answer\":\"xyz\",\"confidence\":0.6}");

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isTrue();
            assertThat(result.token()).isEqualTo("abcd");
            assertThat(result.provider()).isEqualTo(CaptchaProvider.LLM_VISION);
        }

        @Test
        @DisplayName("routes to the math prompt when the classifier says MATH")
        void classifiedAsMath_usesMathPrompt() throws Exception {
            when(preprocessor.enhance(any())).thenReturn(enhancedBytes);
            when(visionClient.analyzeImage(eq("classify?"), anyString(), eq("image/png")))
                    .thenReturn("MATH");
            when(visionClient.analyzeImage(eq("math-prompt"), anyString(), eq("image/png")))
                    .thenReturn("{\"answer\":\"7\",\"confidence\":0.9}");

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isTrue();
            assertThat(result.token()).isEqualTo("7");
        }

        @Test
        @DisplayName("fails with no consensus when votes never reach majority agreement")
        void noMajority_returnsFailure() throws Exception {
            when(preprocessor.enhance(any())).thenReturn(enhancedBytes);
            when(visionClient.analyzeImage(eq("classify?"), anyString(), eq("image/png")))
                    .thenReturn("TEXT");
            when(visionClient.analyzeImage(eq("text-prompt"), anyString(), eq("image/png")))
                    .thenReturn(
                            "{\"answer\":\"aaa\",\"confidence\":0.9}",
                            "{\"answer\":\"bbb\",\"confidence\":0.9}",
                            "{\"answer\":\"ccc\",\"confidence\":0.9}");

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("no consensus");
        }

        @Test
        @DisplayName("fails when the winning answer's confidence is below the minimum threshold")
        void belowConfidenceThreshold_returnsFailure() throws Exception {
            when(preprocessor.enhance(any())).thenReturn(enhancedBytes);
            when(visionClient.analyzeImage(eq("classify?"), anyString(), eq("image/png")))
                    .thenReturn("TEXT");
            when(visionClient.analyzeImage(eq("text-prompt"), anyString(), eq("image/png")))
                    .thenReturn(
                            "{\"answer\":\"abcd\",\"confidence\":0.2}",
                            "{\"answer\":\"abcd\",\"confidence\":0.1}",
                            "{\"answer\":\"xyz\",\"confidence\":0.6}");

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Confidence");
        }

        @Test
        @DisplayName("runs a single pass and skips majority checks when voting is disabled")
        void votingDisabled_singlePass() throws Exception {
            setVotingEnabled(false);
            when(preprocessor.enhance(any())).thenReturn(enhancedBytes);
            when(visionClient.analyzeImage(eq("classify?"), anyString(), eq("image/png")))
                    .thenReturn("TEXT");
            when(visionClient.analyzeImage(eq("text-prompt"), anyString(), eq("image/png")))
                    .thenReturn("{\"answer\":\"abcd\",\"confidence\":0.9}");

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isTrue();
            assertThat(result.token()).isEqualTo("abcd");
        }

        @Test
        @DisplayName("defaults classification to TEXT when the classifier call throws")
        void classifierThrows_defaultsToText() throws Exception {
            when(preprocessor.enhance(any())).thenReturn(enhancedBytes);
            when(visionClient.analyzeImage(eq("classify?"), anyString(), eq("image/png")))
                    .thenThrow(new RuntimeException("vision down"));
            when(visionClient.analyzeImage(eq("text-prompt"), anyString(), eq("image/png")))
                    .thenReturn("{\"answer\":\"abcd\",\"confidence\":0.9}");

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isTrue();
            assertThat(result.token()).isEqualTo("abcd");
        }
    }

    @Nested
    @DisplayName("solve() — failure paths")
    class SolveFailures {

        @BeforeEach
        void enable() {
            when(visionClient.isAvailable()).thenReturn(true);
        }

        @Test
        @DisplayName("returns a failure solution when the vision client is unavailable mid-solve")
        void llmUnavailableDuringSolve_returnsFailure() throws Exception {
            when(preprocessor.enhance(any())).thenThrow(new java.io.IOException("bad image"));

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("bad image");
        }

        @Test
        @DisplayName("returns a failure solution carrying the message when vision throws LlmUnavailableException")
        void llmUnavailableException_isCaught() throws Exception {
            when(preprocessor.enhance(any())).thenReturn(enhancedBytes);
            when(visionClient.analyzeImage(eq("classify?"), anyString(), eq("image/png")))
                    .thenReturn("TEXT");
            when(visionClient.analyzeImage(eq("text-prompt"), anyString(), eq("image/png")))
                    .thenThrow(new LlmUnavailableException("no api key"));

            CaptchaSolution result = solver.solve(challenge());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("no consensus");
        }
    }
}
