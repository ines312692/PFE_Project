package com.elyadata.feature.job.infrastructure.stream;

import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.service.port.JobDispatchPort;
import com.elyadata.feature.job.domain.service.port.JobReconciliationPort;
import com.elyadata.feature.job.domain.service.port.JobReconciliationPort.StaleJob;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Slf4j
@ApplicationScoped
public class JobReconciliationScheduler {
    private static final String LEASE_PREFIX = "job:lease:";
    private static final String MARKER_PREFIX = "job:reconcile:";


    private static final Set<JobStatus> ORPHAN_CANDIDATES =
            EnumSet.of(JobStatus.QUEUED, JobStatus.RUNNING);

    private final ValueCommands<String, String> values;
    private final KeyCommands<String> keys;
    private final ReconciliationConfig config;
    private final JobReconciliationPort reconciliationPort;
    private final JobDispatchPort dispatchPort;

    public JobReconciliationScheduler(RedisDataSource redisDataSource,
                                      ReconciliationConfig config,
                                      JobReconciliationPort reconciliationPort,
                                      JobDispatchPort dispatchPort) {
        this.values = redisDataSource.value(String.class);
        this.keys = redisDataSource.key();
        this.config = config;
        this.reconciliationPort = reconciliationPort;
        this.dispatchPort = dispatchPort;
    }


    @Scheduled(every = "{scraper.reconciliation.scan-interval}",
            delayed = "2m",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcile() {
        if (!config.enabled()) return;

        Instant threshold = Instant.now().minus(config.staleAfter());
        List<StaleJob> candidates;
        try {
            candidates = reconciliationPort.findStale(
                    ORPHAN_CANDIDATES, threshold, config.batchSize());
        } catch (Exception e) {
            log.error("[RECONCILE] lecture des jobs suspects echouee: {}", e.getMessage(), e);
            return;
        }

        if (candidates.isEmpty()) {
            log.debug("[RECONCILE] aucun job suspect (seuil={})", threshold);
            return;
        }

        Outcome outcome = new Outcome();
        for (StaleJob job : candidates) {
            handleCandidate(job, outcome);
        }

        log.info("[RECONCILE] {} candidat(s) — {} redispatche(s), {} vivant(s), {} deja marque(s), {} echec(s)",
                candidates.size(), outcome.redispatched, outcome.alive, outcome.marked, outcome.failed);
    }

    private void handleCandidate(StaleJob job, Outcome outcome) {
        if (hasLiveLease(job.id())) {
            outcome.alive++;
            log.debug("[RECONCILE] job={} status={} — bail vivant, execution en cours",
                    job.id(), job.status());
            return;
        }

        if (!claimMarker(job.id())) {
            outcome.marked++;
            log.debug("[RECONCILE] job={} — deja redispatche recemment, skip", job.id());
            return;
        }

        try {
            dispatchPort.dispatch(job.id());
            outcome.redispatched++;
            log.warn("[RECONCILE] job={} status={} updatedAt={} — orphelin detecte, redispatche",
                    job.id(), job.status(), job.updatedAt());
        } catch (Exception e) {
            outcome.failed++;
            releaseMarker(job.id());
            log.error("[RECONCILE] redispatch echoue job={}: {}", job.id(), e.getMessage(), e);
        }
    }

    private boolean hasLiveLease(UUID jobId) {
        try {
            return values.get(LEASE_PREFIX + jobId) != null;
        } catch (Exception e) {
            log.warn("[RECONCILE] lecture du bail impossible job={} — job suppose vivant: {}",
                    jobId, e.getMessage());
            return true;
        }
    }

    private boolean claimMarker(UUID jobId) {
        String key = MARKER_PREFIX + jobId;
        try {
            boolean won = values.setnx(key, Instant.now().toString());
            if (won) keys.expire(key, config.markerTtl());
            return won;
        } catch (Exception e) {
            log.warn("[RECONCILE] pose du marqueur impossible job={}: {}", jobId, e.getMessage());
            return false;
        }
    }

    private void releaseMarker(UUID jobId) {
        try {
            keys.del(MARKER_PREFIX + jobId);
        } catch (Exception e) {
            log.debug("[RECONCILE] liberation du marqueur echouee job={}: {}", jobId, e.getMessage());
        }
    }

    private static final class Outcome {
        int redispatched;
        int alive;
        int marked;
        int failed;
    }
}
