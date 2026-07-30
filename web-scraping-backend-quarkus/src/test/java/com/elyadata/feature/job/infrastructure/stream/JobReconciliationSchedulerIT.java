package com.elyadata.feature.job.infrastructure.stream;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.JobPriority;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobTriggerType;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.job.infrastructure.entity.JobEntity;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(JobReconciliationSchedulerIT.ReconciliationTestProfile.class)
class JobReconciliationSchedulerIT {

    private static final String LEASE_PREFIX = "job:lease:";
    private static final String MARKER_PREFIX = "job:reconcile:";

    public static class ReconciliationTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("quarkus.micrometer.enabled", "true"),
                    Map.entry("quarkus.micrometer.export.prometheus.enabled", "false"),
                    Map.entry("scraper.stream.enabled", "false"),
                    Map.entry("scraper.reconciliation.enabled", "true"),
                    Map.entry("scraper.reconciliation.scan-interval", "24h"),
                    Map.entry("scraper.reconciliation.stale-after", "15m"),
                    Map.entry("scraper.reconciliation.marker-ttl", "30m"));
        }
    }

    private JobReconciliationScheduler scheduler;
    private EntityManager em;
    private StreamCommands<String, String, String> streams;
    private ValueCommands<String, String> values;
    private KeyCommands<String> keys;

    /**
     * Resolution programmatique via {@link Arc} plutot que par champs
     * {@code @Inject} : JUnit 5 pilote l'instanciation de la classe de test,
     * l'injection par constructeur n'est donc pas disponible. Le conteneur CDI
     * est deja demarre quand {@code @BeforeEach} s'execute.
     */
    @BeforeEach
    void setUp() {
        scheduler = Arc.container().instance(JobReconciliationScheduler.class).get();
        em = Arc.container().instance(EntityManager.class).get();

        RedisDataSource redis = Arc.container().instance(RedisDataSource.class).get();
        streams = redis.stream(String.class, String.class, String.class);
        values = redis.value(String.class);
        keys = redis.key();
    }

    @Test
    @DisplayName("Un job QUEUED perime sans bail est redispatche sur le stream")
    void staleQueuedJobWithoutLeaseIsRedispatched() {
        UUID jobId = persistJob(JobStatus.QUEUED, Instant.now().minus(1, ChronoUnit.HOURS));
        long before = streamLength();

        scheduler.reconcile();

        assertThat(streamLength())
                .as("un message doit avoir ete pousse sur %s", RedisStreamNames.JOB_RUN)
                .isEqualTo(before + 1);
        assertThat(keys.exists(MARKER_PREFIX + jobId))
                .as("le marqueur anti-rejeu doit avoir ete pose")
                .isTrue();
    }

    @Test
    @DisplayName("Un job RUNNING dont le bail est vivant n'est pas redispatche")
    void staleRunningJobWithLiveLeaseIsLeftAlone() {
        UUID jobId = persistJob(JobStatus.RUNNING, Instant.now().minus(2, ChronoUnit.HOURS));
        values.set(LEASE_PREFIX + jobId, "some-live-worker");
        long before = streamLength();

        scheduler.reconcile();

        assertThat(streamLength())
                .as("un job avec bail vivant est en cours d'execution, pas orphelin")
                .isEqualTo(before);
        assertThat(keys.exists(MARKER_PREFIX + jobId)).isFalse();
    }

    @Test
    @DisplayName("Le marqueur empeche le redispatch repete au scan suivant")
    void alreadyReconciledJobIsNotRedispatchedTwice() {
        persistJob(JobStatus.QUEUED, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.reconcile();
        long afterFirst = streamLength();
        scheduler.reconcile();

        assertThat(streamLength())
                .as("le second scan doit etre inoperant tant que le marqueur vit")
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("Un job QUEUED recent n'est pas considere comme orphelin")
    void recentQueuedJobIsIgnored() {
        persistJob(JobStatus.QUEUED, Instant.now().minus(1, ChronoUnit.MINUTES));
        long before = streamLength();

        scheduler.reconcile();

        assertThat(streamLength()).isEqualTo(before);
    }

    @Test
    @DisplayName("Un job COMPLETED perime n'est jamais redispatche")
    void staleCompletedJobIsIgnored() {
        persistJob(JobStatus.COMPLETED, Instant.now().minus(3, ChronoUnit.HOURS));
        long before = streamLength();

        scheduler.reconcile();

        assertThat(streamLength()).isEqualTo(before);
    }

    private long streamLength() {
        try {
            return streams.xlen(RedisStreamNames.JOB_RUN);
        } catch (Exception e) {
            return 0L;
        }
    }

    private UUID persistJob(JobStatus status, Instant updatedAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            JobEntity entity = JobEntity.builder()
                    .name("reconcile-" + UUID.randomUUID())
                    .targetUrl("https://example.invalid/")
                    .jobType(JobType.SCRAPER)
                    .status(status)
                    .priority(JobPriority.NORMAL)
                    .triggerType(JobTriggerType.MANUAL)
                    .extractionMode(ExtractionMode.CSS_SELECTOR)
                    .createdBy(UUID.randomUUID())
                    .build();
            em.persist(entity);
            em.flush();

            em.createNativeQuery("UPDATE jobs SET updated_at = :ts WHERE id = :id")
                    .setParameter("ts", updatedAt)
                    .setParameter("id", entity.getId())
                    .executeUpdate();
            em.clear();

            return entity.getId();
        });
    }
}