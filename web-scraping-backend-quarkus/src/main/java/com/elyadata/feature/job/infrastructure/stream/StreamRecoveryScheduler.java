package com.elyadata.feature.job.infrastructure.stream;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.ClaimedMessages;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

@Slf4j
@ApplicationScoped
public class StreamRecoveryScheduler {

    private final StreamCommands<String, String, String> streams;
    private final StreamConfig config;
    private final ConsumerIdentity identity;
    private final ScrapeWorkerPool pool;
    private final JobRunWorker worker;
    private final boolean enabled;

    public StreamRecoveryScheduler(RedisDataSource redisDataSource, StreamConfig config, ConsumerIdentity identity,
                                   ScrapeWorkerPool pool, JobRunWorker worker) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
        this.config = config;
        this.identity = identity;
        this.pool = pool;
        this.worker = worker;
        this.enabled = config.enabled();
    }

    @Scheduled(every = "{scraper.stream.recovery.scan-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reclaimOrphans() {
        if (!enabled) return;

        Duration minIdle = config.recovery().claimMinIdle();
        String cursor = "0-0";

        try {
            while (pool.freeSlots() > 0) {
                int batch = Math.min(pool.freeSlots(), config.recovery().count());
                ClaimedMessages<String, String, String> claimed = streams.xautoclaim(
                        RedisStreamNames.JOB_RUN, config.jobRun().group(), identity.getName(),
                        minIdle, cursor, batch);

                List<StreamMessage<String, String, String>> messages = claimed.getMessages();
                if (messages.isEmpty()) return;

                log.warn("[STREAM][RECOVERY] {} message(s) orphelin(s) repris depuis la PEL", messages.size());
                if (!submitMessages(messages)) return;

                cursor = claimed.getId();
                if ("0-0".equals(cursor)) return;
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("NOGROUP")) {
                log.debug("[STREAM][RECOVERY] group pas prêt — retry au prochain scan");
                return;
            }
            log.warn("[STREAM][RECOVERY] XAUTOCLAIM failed: {}", msg);
        }
    }

    private boolean submitMessages(List<StreamMessage<String, String, String>> messages) {
        for (StreamMessage<String, String, String> msg : messages) {
            if (!pool.submit(() -> worker.process(msg))) {
                return false;
            }
        }
        return true;
    }
}