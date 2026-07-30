package com.elyadata.feature.llmusage.infrastructure.adapter;

import com.elyadata.feature.llmusage.application.dto.ProjectLlmUsageResponse;
import com.elyadata.feature.llmusage.application.dto.ProjectLlmUsageResponse.FeatureUsage;
import com.elyadata.feature.llmusage.application.dto.ProjectLlmUsageResponse.ModelUsage;
import com.elyadata.feature.llmusage.domain.service.port.ProjectLlmUsagePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProjectLlmUsageAdapter implements ProjectLlmUsagePort {

    private final EntityManager em;

    public ProjectLlmUsageAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public ProjectLlmUsageResponse aggregateForProject(UUID projectId, Instant from, Instant to) {
        Object[] t = (Object[]) em.createNativeQuery("""
                SELECT
                    COUNT(*)                                              AS total_calls,
                    COUNT(*) FILTER (WHERE lc.outcome <> 'SUCCESS')       AS failed_calls,
                    COALESCE(SUM(lc.prompt_tokens), 0)                    AS prompt_tokens,
                    COALESCE(SUM(lc.completion_tokens), 0)                AS completion_tokens,
                    COALESCE(SUM(lc.total_tokens), 0)                     AS total_tokens,
                    COALESCE(SUM(lc.estimated_cost), 0)                   AS total_cost,
                    COALESCE(percentile_cont(0.5) WITHIN GROUP (ORDER BY lc.latency_ms), 0)  AS p50,
                    COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY lc.latency_ms), 0) AS p95
                FROM llm_calls lc
                LEFT JOIN jobs j ON j.id = lc.job_id
                WHERE COALESCE(lc.project_id, j.project_id) = :pid
                  AND lc.created_at >= :from AND lc.created_at < :to
                """)
                .setParameter("pid", projectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();

        long totalCalls   = ((Number) t[0]).longValue();
        long failedCalls  = ((Number) t[1]).longValue();
        long promptTokens = ((Number) t[2]).longValue();
        long complTokens  = ((Number) t[3]).longValue();
        long totalTokens  = ((Number) t[4]).longValue();
        BigDecimal cost   = t[5] != null ? new BigDecimal(t[5].toString()) : BigDecimal.ZERO;
        long p50          = ((Number) t[6]).longValue();
        long p95          = ((Number) t[7]).longValue();
        double failureRate = totalCalls > 0 ? (double) failedCalls / totalCalls : 0.0;

        return new ProjectLlmUsageResponse(
                totalCalls, failedCalls, failureRate,
                promptTokens, complTokens, totalTokens, cost,
                p50, p95,
                groupBy("feature", projectId, from, to, true),
                groupByModel(projectId, from, to)
        );
    }

    private List<FeatureUsage> groupBy(String col, UUID pid, Instant from, Instant to, boolean feature) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = groupQuery(col, pid, from, to).getResultList();
        List<FeatureUsage> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new FeatureUsage(
                    (String) r[0],
                    ((Number) r[1]).longValue(),
                    ((Number) r[2]).longValue(),
                    r[3] != null ? new BigDecimal(r[3].toString()) : BigDecimal.ZERO));
        }
        return out;
    }

    private List<ModelUsage> groupByModel(UUID pid, Instant from, Instant to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = groupQuery("model", pid, from, to).getResultList();
        List<ModelUsage> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new ModelUsage(
                    (String) r[0],
                    ((Number) r[1]).longValue(),
                    ((Number) r[2]).longValue(),
                    r[3] != null ? new BigDecimal(r[3].toString()) : BigDecimal.ZERO));
        }
        return out;
    }

    private Query groupQuery(String col, UUID pid, Instant from, Instant to) {
        return em.createNativeQuery("""
                SELECT lc.%s AS k,
                       COUNT(*) AS calls,
                       COALESCE(SUM(lc.total_tokens), 0) AS tokens,
                       COALESCE(SUM(lc.estimated_cost), 0) AS cost
                FROM llm_calls lc
                LEFT JOIN jobs j ON j.id = lc.job_id
                WHERE COALESCE(lc.project_id, j.project_id) = :pid
                  AND lc.created_at >= :from AND lc.created_at < :to
                GROUP BY lc.%s
                ORDER BY cost DESC
                """.formatted(col, col))
                .setParameter("pid", pid)
                .setParameter("from", from)
                .setParameter("to", to);
    }
}