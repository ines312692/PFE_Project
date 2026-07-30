package com.elyadata.common.llm.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class LlmMetrics {

    @Inject
    MeterRegistry registry;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public void record(String provider, String model, LlmFeature feature, LlmCallOutcome outcome,
                       Integer promptTokens, Integer completionTokens, long latencyMs, BigDecimal cost) {

        callCounter(provider, model, feature, outcome).increment();

        if (promptTokens != null && promptTokens > 0) {
            tokenCounter(provider, model, feature, "prompt").increment(promptTokens);
        }
        if (completionTokens != null && completionTokens > 0) {
            tokenCounter(provider, model, feature, "completion").increment(completionTokens);
        }

        latencyTimer(provider, model, feature).record(latencyMs, TimeUnit.MILLISECONDS);

        if (cost != null && cost.signum() > 0) {
            costCounter(provider, model).increment(cost.doubleValue());
        }
    }

    private Counter callCounter(String provider, String model, LlmFeature feature, LlmCallOutcome outcome) {
        return counters.computeIfAbsent(key("calls", provider, model, feature.name(), outcome.name()),
                k -> Counter.builder("llm.calls.total")
                        .tag("provider", provider).tag("model", model)
                        .tag("feature", feature.name()).tag("outcome", outcome.name())
                        .register(registry));
    }

    private Counter tokenCounter(String provider, String model, LlmFeature feature, String kind) {
        return counters.computeIfAbsent(key("tokens", provider, model, feature.name(), kind),
                k -> Counter.builder("llm.tokens.total")
                        .tag("provider", provider).tag("model", model)
                        .tag("feature", feature.name()).tag("kind", kind)
                        .register(registry));
    }

    private Counter costCounter(String provider, String model) {
        return counters.computeIfAbsent(key("cost", provider, model),
                k -> Counter.builder("llm.cost.total")
                        .tag("provider", provider).tag("model", model)
                        .register(registry));
    }

    private Timer latencyTimer(String provider, String model, LlmFeature feature) {
        return timers.computeIfAbsent(key("latency", provider, model, feature.name()),
                k -> Timer.builder("llm.call.latency")
                        .tag("provider", provider).tag("model", model)
                        .tag("feature", feature.name())
                        .register(registry));
    }

    private static String key(String... parts) {
        return String.join("|", parts);
    }
}