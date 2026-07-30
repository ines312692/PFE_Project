package com.elyadata.feature.job.infrastructure.stream;

import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.job.domain.service.port.JobPort;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.quarkus.redis.datasource.stream.XReadGroupArgs;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class JobRunWorker {

    private static final Duration LEASE_TTL = Duration.ofSeconds(90);
    private static final String LEASE_PREFIX = "job:lease:";

    private final StreamCommands<String, String, String> streams;
    private final ValueCommands<String, String> values;
    private final KeyCommands<String> keys;
    private final StreamConfig config;
    private final ConsumerIdentity identity;
    private final ScrapeWorkerPool pool;
    private final StreamConsumerSupport support;
    private final JobPort jobPort;
    private final JobService jobService;
    private final boolean enabled;

    private final Set<UUID> activeLeases = ConcurrentHashMap.newKeySet();

    public JobRunWorker(RedisDataSource redisDataSource, StreamConfig config, ConsumerIdentity identity,
                        ScrapeWorkerPool pool, StreamConsumerSupport support,
                        JobPort jobPort, JobService jobService) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
        this.values = redisDataSource.value(String.class);
        this.keys = redisDataSource.key();
        this.config = config;
        this.identity = identity;
        this.pool = pool;
        this.support = support;
        this.jobPort = jobPort;
        this.jobService = jobService;
        this.enabled = config.enabled();
    }

    @Scheduled(every = "{scraper.stream.job-run.poll-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        if (!enabled) return;

        int free = pool.freeSlots();
        if (free <= 0) return;

        List<StreamMessage<String, String, String>> msgs = readNew(free);
        if (msgs.isEmpty()) return;

        log.debug("[STREAM] Polled {} message(s) (free slots={})", msgs.size(), free);
        for (StreamMessage<String, String, String> msg : msgs) {
            if (!pool.submit(() -> process(msg))) break;
        }
    }

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void renewLeases() {
        if (!enabled || activeLeases.isEmpty()) return;
        for (UUID jobId : activeLeases) {
            String key = LEASE_PREFIX + jobId;
            try {
                if (identity.getName().equals(values.get(key))) {
                    keys.expire(key, LEASE_TTL);
                } else {
                    log.warn("[STREAM][LEASE] lease job={} plus détenu par ce nœud — abandon local", jobId);
                    activeLeases.remove(jobId);
                }
            } catch (Exception e) {
                log.warn("[STREAM][LEASE] renew failed job={}: {}", jobId, e.getMessage());
            }
        }
    }

    private List<StreamMessage<String, String, String>> readNew(int count) {
        try {
            return streams.xreadgroup(
                    config.jobRun().group(), identity.getName(),
                    RedisStreamNames.JOB_RUN, ">",
                    new XReadGroupArgs().count(count));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("NOGROUP")) {
                support.ensureGroup(RedisStreamNames.JOB_RUN, config.jobRun().group());
                return List.of();
            }
            log.warn("[STREAM] XREADGROUP failed: {}", msg);
            return List.of();
        }
    }

    void process(StreamMessage<String, String, String> msg) {
        String messageId = msg.id();
        Map<String, String> payload = msg.payload();
        String rawJobId = payload.get(RedisStreamNames.FIELD_JOB_ID);

        if (rawJobId == null || rawJobId.isBlank()) {
            support.handleFailure(RedisStreamNames.JOB_RUN, config.jobRun().group(), RedisStreamNames.JOB_RUN_DLQ,
                    messageId, payload, Long.MAX_VALUE, config.jobRun().maxDeliveries(), "missing jobId");
            return;
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(rawJobId);
        } catch (IllegalArgumentException e) {
            support.handleFailure(RedisStreamNames.JOB_RUN, config.jobRun().group(), RedisStreamNames.JOB_RUN_DLQ,
                    messageId, payload, Long.MAX_VALUE, config.jobRun().maxDeliveries(), "invalid jobId: " + rawJobId);
            return;
        }
        Optional<Job> maybeJob = jobPort.findById(jobId);
        if (maybeJob.isEmpty()) {
            log.info("[STREAM] Job {} n'existe plus — ack msg {}", jobId, messageId);
            support.ack(RedisStreamNames.JOB_RUN, config.jobRun().group(), messageId);
            return;
        }
        if (isAlreadyHandled(maybeJob.get())) {
            log.info("[STREAM] Job {} status={} — skip, ack msg {}",
                    jobId, maybeJob.get().status(), messageId);
            support.ack(RedisStreamNames.JOB_RUN, config.jobRun().group(), messageId);
            return;
        }

        if (!acquireLease(jobId)) {
            log.info("[STREAM] Job {} déjà en cours (lease détenu ailleurs) — skip msg {} sans ack",
                    jobId, messageId);
            return;
        }

        long deliveryCount = support.recordAttempt(RedisStreamNames.JOB_RUN, messageId);

        try {
            log.info("[STREAM] Exécution job={} (msg={} livraison={})", jobId, messageId, deliveryCount);
            jobService.runJobSync(jobId);

            support.ack(RedisStreamNames.JOB_RUN, config.jobRun().group(), messageId);
            log.info("[STREAM] Job {} terminé — ack msg {}", jobId, messageId);

        } catch (Exception e) {
            log.error("[STREAM] Job {} en échec (msg={} livraison={}): {}",
                    jobId, messageId, deliveryCount, e.getMessage(), e);

            // Dernière livraison : le message part en DLQ, l'abandon est définitif.
            // Le statut en base doit le refléter, sinon le job reste RUNNING alors
            // qu'aucun message ne le porte plus — JobReconciliationScheduler le
            // verrait comme orphelin et le relancerait indéfiniment, à raison d'un
            // navigateur lancé toutes les 30 minutes.
            if (deliveryCount >= config.jobRun().maxDeliveries()) {
                markFailed(jobId, e.getMessage());
            }

            support.handleFailure(RedisStreamNames.JOB_RUN, config.jobRun().group(), RedisStreamNames.JOB_RUN_DLQ,
                    messageId, payload, deliveryCount, config.jobRun().maxDeliveries(), e.getMessage());
        } finally {
            releaseLease(jobId);
        }
    }

    private void markFailed(UUID jobId, String reason) {
        try {
            QuarkusTransaction.requiringNew().run(() ->
                    jobPort.findById(jobId).ifPresent(job ->
                            jobPort.update(job.toBuilder().status(JobStatus.FAILED).build())));
            log.warn("[STREAM] Job {} → FAILED (livraisons épuisées: {})", jobId, reason);
        } catch (Exception e) {
            log.error("[STREAM] Impossible de marquer job={} en FAILED: {}", jobId, e.getMessage());
        }
    }

    private boolean acquireLease(UUID jobId) {
        String key = LEASE_PREFIX + jobId;
        try {
            values.set(key, identity.getName(),
                    new SetArgs().nx().ex(LEASE_TTL.toSeconds()));
            boolean won = identity.getName().equals(values.get(key));
            if (won) {
                activeLeases.add(jobId);
            }
            return won;
        } catch (Exception e) {
            log.warn("[STREAM][LEASE] acquire failed job={}: {}", jobId, e.getMessage());
            return false;
        }
    }

    private void releaseLease(UUID jobId) {
        String key = LEASE_PREFIX + jobId;
        try {
            String owner = values.get(key);
            if (identity.getName().equals(owner)) {
                keys.del(key);
            }
        } catch (Exception e) {
            log.warn("[STREAM][LEASE] release failed job={}: {}", jobId, e.getMessage());
        } finally {
            activeLeases.remove(jobId);
        }
    }

    private boolean isAlreadyHandled(Job job) {
        return job.status() == JobStatus.COMPLETED || job.status() == JobStatus.CANCELLED;
    }
}