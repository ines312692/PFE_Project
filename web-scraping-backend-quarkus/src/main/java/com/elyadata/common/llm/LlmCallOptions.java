package com.elyadata.common.llm;

import java.time.Duration;


public record LlmCallOptions(String model, double temperature, Duration timeout) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public LlmCallOptions {
        if (timeout == null) timeout = DEFAULT_TIMEOUT;
    }

    public static LlmCallOptions defaults() {
        return new LlmCallOptions(null, 0.0, DEFAULT_TIMEOUT);
    }

    public static LlmCallOptions of(String model, double temperature, Duration timeout) {
        return new LlmCallOptions(model, temperature, timeout);
    }

    public static LlmCallOptions of(String model, double temperature, int timeoutSeconds) {
        return new LlmCallOptions(model, temperature, Duration.ofSeconds(timeoutSeconds));
    }

    public String cacheKey(String fallbackModel) {
        String m = (model == null || model.isBlank()) ? fallbackModel : model;
        return m + "|" + temperature + "|" + timeout.toSeconds();
    }

    public String resolveModel(String fallbackModel) {
        return (model == null || model.isBlank()) ? fallbackModel : model;
    }
}