package com.elyadata.common.llm.observability;

import com.elyadata.common.utils.LogsConstant;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class LlmCallRecorder {

    private static final int MAX_ERROR_LEN = 1000;
    private static final int COST_SCALE = 6;
    private static final BigDecimal PER_MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal ZERO_COST = BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);


    @ConfigProperty(name = "llm.pricing")
    Optional<Map<String, String>> pricing;

    private final LlmMetrics metrics;
    private final LlmCallRepository repository;
    private final LlmCallContext context;

    private final Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();

    public LlmCallRecorder(LlmMetrics metrics, LlmCallRepository repository, LlmCallContext context) {
        this.metrics = metrics;
        this.repository = repository;
        this.context = context;
    }

    public void record(String provider, String model, long latencyMs, LlmCallOutcome outcome,
                       Integer promptTokens, Integer completionTokens, Integer totalTokens,
                       int promptChars, int responseChars, String error) {

        LlmCallContext.Scope scope = context.current().orElse(null);
        LlmFeature feature = scope != null ? scope.feature() : LlmFeature.UNKNOWN;
        UUID jobId     = pick(scope != null ? scope.jobId() : null,  LogsConstant.Keys.JOB_ID);
        UUID userId    = pick(scope != null ? scope.userId() : null, LogsConstant.Keys.USER_ID);
        UUID projectId = scope != null ? scope.projectId() : null;

        Integer total = totalTokens != null ? totalTokens : sum(promptTokens, completionTokens);
        BigDecimal cost = estimateCost(model, promptTokens, completionTokens);

        try {
            metrics.record(provider, model, feature, outcome, promptTokens, completionTokens, latencyMs, cost);
        } catch (Exception e) {
            log.debug("[LLM][METRICS] record failed: {}", e.getMessage());
        }
        LlmCallEntity entity = LlmCallEntity.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .provider(provider)
                .model(model)
                .feature(feature.name())
                .outcome(outcome.name())
                .jobId(jobId)
                .projectId(projectId)
                .userId(userId)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(total)
                .promptChars(promptChars)
                .responseChars(responseChars)
                .latencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE))
                .estimatedCost(cost)
                .errorMessage(truncate(error))
                .build();

        try {
            QuarkusTransaction.requiringNew().run(() -> repository.persist(entity));
        } catch (Exception e) {
            log.debug("[LLM][PERSIST] llm_call persist failed (ignored): {}", e.getMessage());
        }
    }

    private BigDecimal estimateCost(String model, Integer promptTokens, Integer completionTokens) {
        if (pricing.isEmpty()) return ZERO_COST;

        BigDecimal cost = BigDecimal.ZERO;
        if (promptTokens != null && promptTokens > 0) {
            cost = cost.add(part(promptTokens, rate(model, "input")));
        }
        if (completionTokens != null && completionTokens > 0) {
            cost = cost.add(part(completionTokens, rate(model, "output")));
        }
        return cost.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal part(int tokens, BigDecimal ratePerMillion) {
        if (ratePerMillion.signum() == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(tokens)
                .multiply(ratePerMillion)
                .divide(PER_MILLION, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(String model, String kind) {
        return rateCache.computeIfAbsent(model + "." + kind, key -> {
            Map<String, String> p = pricing.orElse(Map.of());
            String value = p.get(key);
            if (value == null) value = p.get("default." + kind);
            if (value == null) return BigDecimal.ZERO;
            try {
                return new BigDecimal(value.trim());
            } catch (NumberFormatException e) {
                log.warn("[LLM][PRICING] Invalid rate for '{}': '{}'", key, value);
                return BigDecimal.ZERO;
            }
        });
    }

    private static Integer sum(Integer a, Integer b) {
        if (a == null && b == null) return null;
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }

    private static UUID pick(UUID fromScope, String mdcKey) {
        if (fromScope != null) return fromScope;
        String raw = MDC.get(mdcKey);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}