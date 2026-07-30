package com.elyadata.common.llm.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmMetrics")
class LlmMetricsTest {

    private SimpleMeterRegistry registry;
    private LlmMetrics metrics;

    @BeforeEach
    void setUp() throws Exception {
        registry = new SimpleMeterRegistry();
        metrics = new LlmMetrics();
        Field f = LlmMetrics.class.getDeclaredField("registry");
        f.setAccessible(true);
        f.set(metrics, registry);
    }

    @Test
    @DisplayName("records a call counter tagged by provider/model/feature/outcome")
    void record_incrementsCallCounter() {
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                100, 50, 250L, BigDecimal.ZERO);

        double count = registry.get("llm.calls.total")
                .tag("provider", "groq").tag("model", "llama-3.3-70b")
                .tag("feature", "EXTRACTION").tag("outcome", "SUCCESS")
                .counter().count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("records prompt and completion token counters when tokens are positive")
    void record_incrementsTokenCounters() {
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                100, 50, 250L, BigDecimal.ZERO);

        double promptTokens = registry.get("llm.tokens.total")
                .tag("provider", "groq").tag("model", "llama-3.3-70b")
                .tag("feature", "EXTRACTION").tag("kind", "prompt")
                .counter().count();
        double completionTokens = registry.get("llm.tokens.total")
                .tag("provider", "groq").tag("model", "llama-3.3-70b")
                .tag("feature", "EXTRACTION").tag("kind", "completion")
                .counter().count();

        assertThat(promptTokens).isEqualTo(100.0);
        assertThat(completionTokens).isEqualTo(50.0);
    }

    @Test
    @DisplayName("does not register token counters when token counts are null or zero")
    void record_skipsTokenCountersWhenAbsent() {
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                null, 0, 10L, BigDecimal.ZERO);

        assertThat(registry.find("llm.tokens.total").counters()).isEmpty();
    }

    @Test
    @DisplayName("records latency on the timer")
    void record_recordsLatencyTimer() {
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                null, null, 123L, BigDecimal.ZERO);

        var timer = registry.get("llm.call.latency")
                .tag("provider", "groq").tag("model", "llama-3.3-70b").tag("feature", "EXTRACTION")
                .timer();

        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(123.0);
    }

    @Test
    @DisplayName("records cost only when cost is positive")
    void record_incrementsCostOnlyWhenPositive() {
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                null, null, 10L, BigDecimal.valueOf(0.05));

        double cost = registry.get("llm.cost.total")
                .tag("provider", "groq").tag("model", "llama-3.3-70b")
                .counter().count();

        assertThat(cost).isEqualTo(0.05);
    }

    @Test
    @DisplayName("does not register a cost counter when cost is zero")
    void record_skipsCostCounterWhenZero() {
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                null, null, 10L, BigDecimal.ZERO);

        assertThat(registry.find("llm.cost.total").counters()).isEmpty();
    }

    @Test
    @DisplayName("accumulates multiple calls into the same counter")
    void record_accumulatesAcrossCalls() {
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                10, 5, 100L, BigDecimal.ZERO);
        metrics.record("groq", "llama-3.3-70b", LlmFeature.EXTRACTION, LlmCallOutcome.SUCCESS,
                10, 5, 100L, BigDecimal.ZERO);

        double count = registry.get("llm.calls.total")
                .tag("provider", "groq").tag("model", "llama-3.3-70b")
                .tag("feature", "EXTRACTION").tag("outcome", "SUCCESS")
                .counter().count();

        assertThat(count).isEqualTo(2.0);
    }
}
