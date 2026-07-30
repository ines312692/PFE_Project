package com.elyadata.common.llm.observability;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@ApplicationScoped
@RequiredArgsConstructor
public class LlmCallRepository {

    private final EntityManager em;

    public void persist(LlmCallEntity entity) {
        em.persist(entity);
    }

    private String projectFilter(UUID projectId) {
        return projectId == null ? "" : " AND COALESCE(lc.project_id, j.project_id) = :projectId ";
    }

    public Object[] summary(UUID projectId, Instant from, Instant to) {
        String sql = """
                SELECT COUNT(*)                                                    AS calls,
                       COALESCE(SUM(lc.prompt_tokens), 0)                          AS prompt_tokens,
                       COALESCE(SUM(lc.completion_tokens), 0)                      AS completion_tokens,
                       COALESCE(SUM(lc.total_tokens), 0)                           AS total_tokens,
                       COALESCE(SUM(lc.estimated_cost), 0)                         AS cost,
                       COALESCE(AVG(lc.latency_ms), 0)                             AS avg_latency,
                       COALESCE(SUM(CASE WHEN lc.outcome = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success,
                       COALESCE(SUM(CASE WHEN lc.outcome = 'RATE_LIMITED' THEN 1 ELSE 0 END), 0) AS rate_limited
                FROM llm_calls lc
                LEFT JOIN jobs j ON j.id = lc.job_id
                WHERE lc.created_at BETWEEN :from AND :to
                """ + projectFilter(projectId);

        Query q = em.createNativeQuery(sql)
                .setParameter("from", from)
                .setParameter("to", to);
        if (projectId != null) q.setParameter("projectId", projectId);
        return (Object[]) q.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> byFeature(UUID projectId, Instant from, Instant to) {
        String sql = """
                SELECT lc.feature,
                       COUNT(*)                                                    AS calls,
                       COALESCE(SUM(lc.total_tokens), 0)                           AS total_tokens,
                       COALESCE(AVG(lc.latency_ms), 0)                             AS avg_latency,
                       COALESCE(SUM(CASE WHEN lc.outcome = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success
                FROM llm_calls lc
                LEFT JOIN jobs j ON j.id = lc.job_id
                WHERE lc.created_at BETWEEN :from AND :to
                """ + projectFilter(projectId) + """
                GROUP BY lc.feature
                ORDER BY calls DESC
                """;
        Query q = em.createNativeQuery(sql)
                .setParameter("from", from).setParameter("to", to);
        if (projectId != null) q.setParameter("projectId", projectId);
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> byModel(UUID projectId, Instant from, Instant to) {
        String sql = """
                SELECT lc.model,
                       COUNT(*)                            AS calls,
                       COALESCE(SUM(lc.total_tokens), 0)   AS total_tokens,
                       COALESCE(SUM(lc.estimated_cost), 0) AS cost
                FROM llm_calls lc
                LEFT JOIN jobs j ON j.id = lc.job_id
                WHERE lc.created_at BETWEEN :from AND :to
                """ + projectFilter(projectId) + """
                GROUP BY lc.model
                ORDER BY calls DESC
                """;
        Query q = em.createNativeQuery(sql)
                .setParameter("from", from).setParameter("to", to);
        if (projectId != null) q.setParameter("projectId", projectId);
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> byProject(Instant from, Instant to) {
        String sql = """
                SELECT COALESCE(lc.project_id, j.project_id)  AS project_id,
                       COUNT(*)                               AS calls,
                       COALESCE(SUM(lc.total_tokens), 0)      AS total_tokens,
                       COALESCE(SUM(lc.estimated_cost), 0)    AS cost,
                       COALESCE(AVG(lc.latency_ms), 0)        AS avg_latency
                FROM llm_calls lc
                LEFT JOIN jobs j ON j.id = lc.job_id
                WHERE lc.created_at BETWEEN :from AND :to
                GROUP BY COALESCE(lc.project_id, j.project_id)
                ORDER BY calls DESC
                """;
        return em.createNativeQuery(sql)
                .setParameter("from", from).setParameter("to", to)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> dailyTokens(UUID projectId, Instant from, Instant to) {
        String sql = """
                SELECT date_trunc('day', lc.created_at)      AS day,
                       COUNT(*)                              AS calls,
                       COALESCE(SUM(lc.total_tokens), 0)     AS total_tokens,
                       COALESCE(SUM(CASE WHEN lc.outcome <> 'SUCCESS' THEN 1 ELSE 0 END), 0) AS failed
                FROM llm_calls lc
                LEFT JOIN jobs j ON j.id = lc.job_id
                WHERE lc.created_at BETWEEN :from AND :to
                """ + projectFilter(projectId) + """
                GROUP BY day
                ORDER BY day
                """;
        Query q = em.createNativeQuery(sql)
                .setParameter("from", from).setParameter("to", to);
        if (projectId != null) q.setParameter("projectId", projectId);
        return q.getResultList();
    }

    public List<LlmCallEntity> recent(UUID projectId, int limit) {
        String jpql = projectId == null
                ? "SELECT c FROM LlmCallEntity c ORDER BY c.createdAt DESC"
                : "SELECT c FROM LlmCallEntity c WHERE c.projectId = :projectId ORDER BY c.createdAt DESC";

        var query = em.createQuery(jpql, LlmCallEntity.class).setMaxResults(limit);
        if (projectId != null) query.setParameter("projectId", projectId);
        return query.getResultList();
    }
}
