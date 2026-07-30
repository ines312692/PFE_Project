package com.elyadata.feature.job.infrastructure.stream;

import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.service.port.EtlRunPort;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
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
public class EtlWorker {

    private static final Duration LEASE_TTL = Duration.ofSeconds(90);
    private static final String LEASE_PREFIX = "etl:lease:";

    private final StreamCommands<String, String, String> streams;
    private final ValueCommands<String, String> values;
    private final KeyCommands<String> keys;
    private final StreamConfig config;
    private final ConsumerIdentity identity;
    private final EtlWorkerPool pool;
    private final StreamConsumerSupport support;
    private final JobPort jobPort;
    private final EtlRunPort etlRunPort;
    private final NotificationService notificationService;
    private final boolean enabled;

    private final Set<UUID> activeLeases = ConcurrentHashMap.newKeySet();

    public EtlWorker(RedisDataSource redisDataSource, StreamConfig config, ConsumerIdentity identity,
                     EtlWorkerPool pool, StreamConsumerSupport support,
                     JobPort jobPort, EtlRunPort etlRunPort,
                     NotificationService notificationService) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
        this.values = redisDataSource.value(String.class);
        this.keys = redisDataSource.key();
        this.config = config;
        this.identity = identity;
        this.pool = pool;
        this.support = support;
        this.jobPort = jobPort;
        this.etlRunPort = etlRunPort;
        this.notificationService = notificationService;
        this.enabled = config.enabled();
    }

    @Scheduled(every = "{scraper.stream.etl.poll-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        if (!enabled) return;

        int free = pool.freeSlots();
        if (free <= 0) return;

        List<StreamMessage<String, String, String>> msgs = readNew(free);
        if (msgs.isEmpty()) return;

        log.debug("[STREAM][ETL] Polled {} message(s) (free slots={})", msgs.size(), free);
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
                    log.warn("[STREAM][ETL][LEASE] lease job={} plus détenu par ce nœud — abandon local", jobId);
                    activeLeases.remove(jobId);
                }
            } catch (Exception e) {
                log.warn("[STREAM][ETL][LEASE] renew failed job={}: {}", jobId, e.getMessage());
            }
        }
    }

    private List<StreamMessage<String, String, String>> readNew(int count) {
        try {
            return streams.xreadgroup(
                    config.etl().group(), identity.getName(),
                    RedisStreamNames.ETL_PROCESS, ">",
                    new XReadGroupArgs().count(count));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("NOGROUP")) {
                support.ensureGroup(RedisStreamNames.ETL_PROCESS, config.etl().group());
                return List.of();
            }
            log.warn("[STREAM][ETL] XREADGROUP failed: {}", msg);
            return List.of();
        }
    }


    void process(StreamMessage<String, String, String> msg) {
        String messageId = msg.id();
        Map<String, String> payload = msg.payload();
        String rawJobId = payload.get(RedisStreamNames.FIELD_JOB_ID);

        if (rawJobId == null || rawJobId.isBlank()) {
            support.handleFailure(RedisStreamNames.ETL_PROCESS, config.etl().group(), RedisStreamNames.ETL_PROCESS_DLQ,
                    messageId, payload, Long.MAX_VALUE, config.etl().maxDeliveries(), "missing jobId");
            return;
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(rawJobId);
        } catch (IllegalArgumentException e) {
            support.handleFailure(RedisStreamNames.ETL_PROCESS, config.etl().group(), RedisStreamNames.ETL_PROCESS_DLQ,
                    messageId, payload, Long.MAX_VALUE, config.etl().maxDeliveries(), "invalid jobId: " + rawJobId);
            return;
        }

        Optional<Job> maybeJob = jobPort.findById(jobId);
        if (maybeJob.isEmpty()) {
            log.info("[STREAM][ETL] Job {} n'existe plus — ack msg {}", jobId, messageId);
            support.ack(RedisStreamNames.ETL_PROCESS, config.etl().group(), messageId);
            return;
        }
        Job job = maybeJob.get();

        if (!acquireLease(jobId)) {
            log.info("[STREAM][ETL] Job {} déjà en cours (lease ailleurs) — skip msg {} sans ack",
                    jobId, messageId);
            return;
        }

        long deliveryCount = support.recordAttempt(RedisStreamNames.ETL_PROCESS, messageId);

        try {
            log.info("[STREAM][ETL] Exécution ETL job={} (msg={} livraison={})", jobId, messageId, deliveryCount);
            etlRunPort.runEtlSync(jobId);

            support.ack(RedisStreamNames.ETL_PROCESS, config.etl().group(), messageId);
            log.info("[STREAM][ETL] Job {} traité — ack msg {}", jobId, messageId);

        } catch (Exception e) {
            log.error("[STREAM][ETL] Job {} en échec (msg={} livraison={}): {}",
                    jobId, messageId, deliveryCount, e.getMessage(), e);
            if (deliveryCount >= config.etl().maxDeliveries()) {
                notifyEtlAbandoned(job, e.getMessage());
            }

            support.handleFailure(RedisStreamNames.ETL_PROCESS, config.etl().group(), RedisStreamNames.ETL_PROCESS_DLQ,
                    messageId, payload, deliveryCount, config.etl().maxDeliveries(), e.getMessage());
        } finally {
            releaseLease(jobId);
        }
    }


    private void notifyEtlAbandoned(Job job, String reason) {
        log.error("[STREAM][ETL] Job {} — pipeline ETL ABANDONNE apres {} livraison(s). "
                        + "Les records bruts restent disponibles, sans transformation.",
                job.id(), config.etl().maxDeliveries());

        if (job.createdBy() == null) return;
        try {
            QuarkusTransaction.requiringNew().run(() ->
                    notificationService.notify(job.createdBy(), job.id(), NotificationType.JOB_FAILED,
                            "Traitement ETL échoué : " + job.name(),
                            "Les données brutes ont bien été extraites, mais le pipeline de "
                                    + "nettoyage n'a pas pu s'exécuter (" + shortReason(reason) + ")."));
        } catch (Exception e) {
            log.warn("[STREAM][ETL] notification d'abandon echouee job={}: {}", job.id(), e.getMessage());
        }
    }

    private static String shortReason(String reason) {
        if (reason == null || reason.isBlank()) return "cause inconnue";
        return reason.length() <= 200 ? reason : reason.substring(0, 200) + "…";
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
            log.warn("[STREAM][ETL][LEASE] acquire failed job={}: {}", jobId, e.getMessage());
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
            log.warn("[STREAM][ETL][LEASE] release failed job={}: {}", jobId, e.getMessage());
        } finally {
            activeLeases.remove(jobId);
        }
    }
}