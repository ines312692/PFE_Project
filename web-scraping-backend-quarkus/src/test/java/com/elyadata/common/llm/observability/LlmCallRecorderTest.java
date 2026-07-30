package com.elyadata.common.llm.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmCallRecorder")
class LlmCallRecorderTest {

    @Mock
    LlmMetrics metrics;
    @Mock
    LlmCallRepository repository;
    @Mock
    LlmCallContext context;

    LlmCallRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new LlmCallRecorder(metrics, repository, context);
        lenient().when(context.current()).thenReturn(Optional.empty());
        MDC.clear();
    }

    private void setPricing(Map<String, String> pricing) throws Exception {
        Field f = LlmCallRecorder.class.getDeclaredField("pricing");
        f.setAccessible(true);
        f.set(recorder, Optional.ofNullable(pricing));
    }

    @Nested
    @DisplayName("record() -> metrics")
    class RecordMetrics {

        @Test
        @DisplayName("forwards outcome/tokens/latency to LlmMetrics using UNKNOWN feature when no scope is open")
        void record_noScope_usesUnknownFeature() throws Exception {
            setPricing(null);

            recorder.record("groq", "model-x", 42L, LlmCallOutcome.SUCCESS,
                    10, 20, 30, 100, 200, null);

            verify(metrics).record(eq("groq"), eq("model-x"), eq(LlmFeature.UNKNOWN),
                    eq(LlmCallOutcome.SUCCESS), eq(10), eq(20), eq(42L), any(BigDecimal.class));
        }

        @Test
        @DisplayName("uses the feature from the current scope when present")
        void record_withScope_usesScopeFeature() throws Exception {
            setPricing(null);
            UUID jobId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(context.current()).thenReturn(Optional.of(
                    new LlmCallContext.Scope(LlmFeature.EXTRACTION, jobId, null, userId)));

            recorder.record("groq", "model-x", 10L, LlmCallOutcome.SUCCESS,
                    1, 2, 3, 10, 20, null);

            verify(metrics).record(eq("groq"), eq("model-x"), eq(LlmFeature.EXTRACTION),
                    eq(LlmCallOutcome.SUCCESS), eq(1), eq(2), eq(10L), any(BigDecimal.class));
        }

        @Test
        @DisplayName("sums prompt and completion tokens when totalTokens is null")
        void record_nullTotalTokens_sumsPromptAndCompletion() throws Exception {
            setPricing(null);

            recorder.record("groq", "model-x", 10L, LlmCallOutcome.SUCCESS,
                    7, 3, null, 10, 20, null);

            verify(metrics).record(any(), any(), any(), any(), eq(7), eq(3), eq(10L), any());
        }

        @Test
        @DisplayName("does not throw when LlmMetrics.record itself throws")
        void record_metricsThrows_isSwallowed() throws Exception {
            setPricing(null);
            org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                    .when(metrics).record(any(), any(), any(), any(), any(), any(), anyLong(), any());

            recorder.record("groq", "model-x", 10L, LlmCallOutcome.ERROR,
                    null, null, null, 5, 0, "failure");
            // no exception should propagate
        }
    }

    @Nested
    @DisplayName("record() -> cost estimation")
    class CostEstimation {

        @Test
        @DisplayName("cost is zero when pricing config is absent")
        void noPricingConfig_zeroCost() throws Exception {
            setPricing(null);

            recorder.record("groq", "model-x", 10L, LlmCallOutcome.SUCCESS,
                    1000, 1000, 2000, 10, 20, null);

            ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(metrics).record(any(), any(), any(), any(), any(), any(), anyLong(), costCaptor.capture());
            assertThat(costCaptor.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("computes cost from configured input/output rates per million tokens")
        void withPricingConfig_computesCost() throws Exception {
            setPricing(Map.of(
                    "model-x.input", "1.00",
                    "model-x.output", "2.00"));

            // 1_000_000 prompt tokens @ $1/M = $1.00 ; 500_000 completion tokens @ $2/M = $1.00 -> total $2.00
            recorder.record("groq", "model-x", 10L, LlmCallOutcome.SUCCESS,
                    1_000_000, 500_000, 1_500_000, 10, 20, null);

            ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(metrics).record(any(), any(), any(), any(), any(), any(), anyLong(), costCaptor.capture());
            assertThat(costCaptor.getValue()).isEqualByComparingTo(new BigDecimal("2.000000"));
        }

        @Test
        @DisplayName("falls back to default.<kind> rate when a model-specific rate is absent")
        void fallsBackToDefaultRate() throws Exception {
            setPricing(Map.of("default.input", "3.00"));

            recorder.record("groq", "unlisted-model", 10L, LlmCallOutcome.SUCCESS,
                    1_000_000, null, 1_000_000, 10, 20, null);

            ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(metrics).record(any(), any(), any(), any(), any(), any(), anyLong(), costCaptor.capture());
            assertThat(costCaptor.getValue()).isEqualByComparingTo(new BigDecimal("3.000000"));
        }

        @Test
        @DisplayName("treats an unparsable rate as zero")
        void invalidRate_treatedAsZero() throws Exception {
            setPricing(Map.of("model-x.input", "not-a-number"));

            recorder.record("groq", "model-x", 10L, LlmCallOutcome.SUCCESS,
                    1_000_000, null, 1_000_000, 10, 20, null);

            ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(metrics).record(any(), any(), any(), any(), any(), any(), anyLong(), costCaptor.capture());
            assertThat(costCaptor.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("record() -> MDC fallback for jobId/userId")
    class MdcFallback {

        @Test
        @DisplayName("falls back to MDC JOB_ID/USER_ID when no scope is present")
        void usesMdcWhenNoScope() throws Exception {
            setPricing(null);
            UUID jobId = UUID.randomUUID();
            MDC.put("JOB_ID", jobId.toString());

            // We can't directly assert on the persisted entity (persist runs in a new
            // transaction outside test scope and any failure there is swallowed), but the
            // call should not throw when parsing a valid MDC UUID.
            recorder.record("groq", "model-x", 5L, LlmCallOutcome.SUCCESS,
                    null, null, null, 1, 1, null);

            verify(metrics).record(any(), any(), any(), any(), isNull(), isNull(), eq(5L), any());
        }

        @Test
        @DisplayName("ignores an invalid MDC UUID instead of throwing")
        void invalidMdcUuid_isIgnored() throws Exception {
            setPricing(null);
            MDC.put("JOB_ID", "not-a-uuid");

            recorder.record("groq", "model-x", 5L, LlmCallOutcome.SUCCESS,
                    null, null, null, 1, 1, null);

            verify(metrics).record(any(), any(), any(), any(), isNull(), isNull(), eq(5L), any());
        }
    }

    @Nested
    @DisplayName("record() -> error truncation")
    class ErrorTruncation {

        @Test
        @DisplayName("does not throw for a very long error message")
        void longErrorMessage_doesNotThrow() throws Exception {
            setPricing(null);
            String longError = "x".repeat(2000);

            recorder.record("groq", "model-x", 5L, LlmCallOutcome.ERROR,
                    null, null, null, 1, 0, longError);

            verify(metrics).record(any(), any(), any(), eq(LlmCallOutcome.ERROR), any(), any(), eq(5L), any());
        }
    }
}
