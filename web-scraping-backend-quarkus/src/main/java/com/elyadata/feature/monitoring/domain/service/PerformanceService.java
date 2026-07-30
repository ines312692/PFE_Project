package com.elyadata.feature.monitoring.domain.service;

import com.elyadata.feature.monitoring.application.dto.PerformanceDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@SuppressWarnings({"java:S2077", "java:S3649"})
public class PerformanceService {

    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";
    private static final String PARAM_USER_ID = "userId";
    private static final String FROM_RESULTS = "FROM scrape_results r";

    private final EntityManager em;

    private record Window(Instant from, Instant to, String granularity, String pgTrunc) {
    }

    private Window resolveWindow(String range) {
        Instant now = Instant.now();
        return switch (range == null ? "24h" : range) {
            case "7d" -> new Window(now.minus(7, ChronoUnit.DAYS), now, "hour", "hour");
            case "30d" -> new Window(now.minus(30, ChronoUnit.DAYS), now, "day", "day");
            default -> new Window(now.minus(24, ChronoUnit.HOURS), now, "hour", "hour");
        };
    }

    public PerformanceDto.Summary summary(String range, UUID userId, boolean admin) {
        Window w = resolveWindow(range);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(SUM(r.pages_crawled), 0)                                   AS total_pages,
                    COALESCE(SUM(jsonb_array_length(r.records)), 0)                     AS total_records,
                    COUNT(*)                                                            AS total_runs,
                    COALESCE(AVG(r.duration_ms), 0)                                     AS avg_duration,
                    COALESCE(
                        PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY r.duration_ms), 0) AS p95_duration,
                    COALESCE(AVG(
                        CASE WHEN r.duration_ms > 0
                             THEN r.pages_crawled / (r.duration_ms / 60000.0)
                             ELSE NULL END), 0)                                         AS avg_crawl_speed,
                    COALESCE(
                        AVG(CASE WHEN r.status = 'completed' THEN 1.0 ELSE 0.0 END), 0) AS success_rate,
                    COUNT(*) FILTER (WHERE r.status = 'completed')                      AS completed_runs,
                    COUNT(*) FILTER (WHERE r.status = 'failed')                         AS failed_runs,
                    COUNT(*) FILTER (WHERE r.status = 'partial')                        AS partial_runs
                FROM scrape_results r
                """);
        appendScope(sql, admin);

        Object[] row = (Object[]) bind(em.createNativeQuery(sql.toString()), w, userId, admin)
                .getSingleResult();

        long totalPages = toLong(row[0]);
        long totalRecords = toLong(row[1]);
        long totalRuns = toLong(row[2]);
        double windowMin = Math.max(1.0, Duration.between(w.from(), w.to()).toSeconds() / 60.0);

        return new PerformanceDto.Summary(
                totalPages / windowMin,
                totalRecords / windowMin,
                toDouble(row[5]),
                toDouble(row[3]),
                toDouble(row[4]),
                toDouble(row[6]),
                totalPages,
                totalRecords,
                totalRuns,
                toLong(row[7]),
                toLong(row[8]),
                toLong(row[9])
        );
    }

    public PerformanceDto.TimeSeries timeseries(String range, UUID userId, boolean admin) {
        Window w = resolveWindow(range);
        Duration span = Duration.between(w.from(), w.to());

        List<PerformanceDto.TimePoint> current =
                bucketed(w.from(), w.to(), w.pgTrunc(), userId, admin);
        List<PerformanceDto.TimePoint> previous =
                bucketed(w.from().minus(span), w.from(), w.pgTrunc(), userId, admin);

        return new PerformanceDto.TimeSeries(
                range == null ? "24h" : range, w.granularity(), current, previous);
    }

    private List<PerformanceDto.TimePoint> bucketed(
            Instant from, Instant to, String trunc, UUID userId, boolean admin) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    date_trunc(:trunc, r.scraped_at)               AS bucket,
                    COALESCE(SUM(r.pages_crawled), 0)              AS pages,
                    COALESCE(SUM(jsonb_array_length(r.records)),0) AS records,
                    COUNT(*)                                       AS runs
                FROM scrape_results r
                """);
        appendScope(sql, admin);
        sql.append(" GROUP BY 1 ORDER BY 1 ASC");

        Query q = em.createNativeQuery(sql.toString())
                .setParameter("trunc", trunc)
                .setParameter(PARAM_FROM, from)
                .setParameter(PARAM_TO, to);
        if (!admin) q.setParameter(PARAM_USER_ID, userId);

        double bucketMin = switch (trunc) {
            case "minute" -> 1.0;
            case "day" -> 1440.0;
            default -> 60.0;
        };

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<PerformanceDto.TimePoint> points = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            long pages = toLong(r[1]);
            long records = toLong(r[2]);
            long runs = toLong(r[3]);
            points.add(new PerformanceDto.TimePoint(
                    toInstant(r[0]), pages, records, runs, pages / bucketMin));
        }
        return points;
    }

    public List<PerformanceDto.EngineBreakdown> byEngine(String range, UUID userId, boolean admin) {
        Window w = resolveWindow(range);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(r.engine_used, 'unknown')            AS engine,
                    COUNT(*)                                      AS runs,
                    COALESCE(SUM(r.pages_crawled), 0)             AS pages,
                    COALESCE(AVG(
                        CASE WHEN r.duration_ms > 0
                             THEN r.pages_crawled / (r.duration_ms / 60000.0)
                             ELSE NULL END), 0)                   AS avg_speed,
                    COALESCE(
                        AVG(CASE WHEN r.status = 'completed' THEN 1.0 ELSE 0.0 END), 0) AS success_rate
                FROM scrape_results r
                """);
        appendScope(sql, admin);
        sql.append(" GROUP BY 1 ORDER BY pages DESC");

        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery(sql.toString()), w, userId, admin)
                .getResultList();

        List<PerformanceDto.EngineBreakdown> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new PerformanceDto.EngineBreakdown(
                    (String) r[0], toLong(r[1]), toLong(r[2]),
                    toDouble(r[3]), toDouble(r[4])));
        }
        return out;
    }


    public List<PerformanceDto.RunPoint> recentRuns(String range, UUID userId, boolean admin) {
        Window w = resolveWindow(range);

        Query q = em.createNativeQuery(recentRunsSql(admin))
                .setParameter(PARAM_FROM, w.from())
                .setParameter(PARAM_TO, w.to());
        if (!admin) q.setParameter(PARAM_USER_ID, userId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<PerformanceDto.RunPoint> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new PerformanceDto.RunPoint(
                    toUuid(r[0]),
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    toLong(r[4]),
                    toLong(r[5]),
                    toLong(r[6]),
                    toDouble(r[7]),
                    toInstant(r[8])
            ));
        }
        Collections.reverse(out);
        return out;
    }

    private String recentRunsSql(boolean admin) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    CAST(r.job_id AS varchar)                     AS job_id,
                    COALESCE(j.name, 'job')                       AS job_name,
                    COALESCE(r.engine_used, 'unknown')            AS engine,
                    r.status                                      AS status,
                    COALESCE(r.pages_crawled, 0)                  AS pages,
                    COALESCE(jsonb_array_length(r.records), 0)    AS records,
                    COALESCE(r.duration_ms, 0)                    AS duration_ms,
                    CASE WHEN r.duration_ms > 0
                         THEN r.pages_crawled / (r.duration_ms / 60000.0)
                         ELSE 0 END                               AS pages_per_min,
                    r.scraped_at                                  AS scraped_at
                FROM scrape_results r
                JOIN jobs j ON j.id = r.job_id
                WHERE r.scraped_at >= :from AND r.scraped_at < :to
                """);
        if (!admin) sql.append(" AND j.created_by = :userId");
        sql.append(" ORDER BY r.scraped_at DESC LIMIT 20");
        return sql.toString();
    }

    private void appendScope(StringBuilder sql, boolean admin) {
        sql.append(" WHERE r.scraped_at >= :from AND r.scraped_at < :to");
        if (!admin) {
            sql.insert(sql.indexOf(FROM_RESULTS) + FROM_RESULTS.length(),
                    " JOIN jobs j ON j.id = r.job_id");
            sql.append(" AND j.created_by = :userId");
        }
    }

    private Query bind(Query q, Window w, UUID userId, boolean admin) {
        q.setParameter(PARAM_FROM, w.from()).setParameter(PARAM_TO, w.to());
        if (!admin) q.setParameter(PARAM_USER_ID, userId);
        return q;
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof BigDecimal bd) return bd.doubleValue();
        return ((Number) o).doubleValue();
    }

    private static UUID toUuid(Object o) {
        if (o == null) return null;
        if (o instanceof UUID u) return u;
        try {
            return UUID.fromString(o.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof java.sql.Timestamp t) return t.toInstant();
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        return Instant.now();
    }
}