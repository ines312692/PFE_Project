package com.elyadata.feature.llmusage.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProjectLlmUsageResponse(
        long totalCalls,
        long failedCalls,
        double failureRate,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal totalCost,
        long latencyP50Ms,
        long latencyP95Ms,
        List<FeatureUsage> byFeature,
        List<ModelUsage> byModel
) {
    public record FeatureUsage(String feature, long calls, long totalTokens, BigDecimal cost) {}
    public record ModelUsage(String model, long calls, long totalTokens, BigDecimal cost) {}
}