package com.elyadata.feature.scraperesult.infrastructure.adapter;

import com.elyadata.feature.scraperesult.application.dto.ProjectScrapeStatsResponse;
import com.elyadata.feature.scraperesult.application.dto.ProjectScrapeStatsResponse.DailyPoint;
import com.elyadata.feature.scraperesult.domain.service.port.ProjectScrapeStatsPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProjectScrapeStatsAdapter implements ProjectScrapeStatsPort {

    private final EntityManager em;

    public ProjectScrapeStatsAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public ProjectScrapeStatsResponse aggregateForProject(UUID projectId, Instant from, Instant to) {
        Object[] t = (Object[]) em.createNativeQuery("""
                SELECT
                    COUNT(*)                                                       AS total_runs,
                    COUNT(*) FILTER (WHERE sr.status = 'completed')                AS completed_runs,
                    COUNT(*) FILTER (WHERE sr.status = 'failed')                   AS failed_runs,
                    COUNT(*) FILTER (WHERE sr.status = 'partial')                  AS partial_runs,
                    COALESCE(SUM(sr.pages_crawled), 0)                             AS total_pages,
                    COALESCE(SUM(jsonb_array_length(sr.records)), 0)               AS total_records,
                    COALESCE(SUM(jsonb_array_length(sr.discovered_files)), 0)      AS total_files,
                    COALESCE(ROUND(AVG(sr.duration_ms)), 0)                        AS avg_duration
                FROM scrape_results sr
                JOIN jobs j ON j.id = sr.job_id
                WHERE j.project_id = :pid
                  AND sr.scraped_at >= :from AND sr.scraped_at < :to
                """)
                .setParameter("pid", projectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();

        long totalRuns     = ((Number) t[0]).longValue();
        long completed     = ((Number) t[1]).longValue();
        long failed        = ((Number) t[2]).longValue();
        long partial       = ((Number) t[3]).longValue();
        long totalPages    = ((Number) t[4]).longValue();
        long totalRecords  = ((Number) t[5]).longValue();
        long totalFiles    = ((Number) t[6]).longValue();
        long avgDuration   = ((Number) t[7]).longValue();
        double successRate = totalRuns > 0 ? (double) completed / totalRuns : 0.0;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT to_char(date_trunc('day', sr.scraped_at), 'YYYY-MM-DD') AS day,
                       COALESCE(SUM(sr.pages_crawled), 0)                       AS pages
                FROM scrape_results sr
                JOIN jobs j ON j.id = sr.job_id
                WHERE j.project_id = :pid
                  AND sr.scraped_at >= :from AND sr.scraped_at < :to
                GROUP BY 1
                ORDER BY 1
                """)
                .setParameter("pid", projectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        List<DailyPoint> trend = new ArrayList<>();
        for (Object[] r : rows) {
            trend.add(new DailyPoint((String) r[0], ((Number) r[1]).longValue()));
        }

        return new ProjectScrapeStatsResponse(
                totalRuns, completed, failed, partial, successRate,
                totalPages, totalRecords, totalFiles, avgDuration, trend);
    }
}