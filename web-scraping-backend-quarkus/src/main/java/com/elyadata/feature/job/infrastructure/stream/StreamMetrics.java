package com.elyadata.feature.job.infrastructure.stream;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.XPendingSummary;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@ApplicationScoped
public class StreamMetrics {

    private final StreamCommands<String, String, String> streams;
    private final StreamConfig config;
    private final boolean enabled;

    private final AtomicLong pending = new AtomicLong(0);
    private final AtomicLong length = new AtomicLong(0);
    private final AtomicLong dlqLength = new AtomicLong(0);

    public StreamMetrics(RedisDataSource redisDataSource, StreamConfig config,
                         ScrapeWorkerPool pool, MeterRegistry registry) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
        this.config = config;
        this.enabled = config.enabled();

        registry.gauge("scraper_stream_pending", pending);
        registry.gauge("scraper_stream_length", length);
        registry.gauge("scraper_stream_dlq_length", dlqLength);
        registry.gauge("scraper_worker_inflight", pool, ScrapeWorkerPool::inFlight);
        registry.gauge("scraper_worker_free_slots", pool, ScrapeWorkerPool::freeSlots);
    }

    @Scheduled(every = "15s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sample() {
        if (!enabled) return;
        try {
            XPendingSummary summary = streams.xpending(RedisStreamNames.JOB_RUN, config.jobRun().group());
            pending.set(summary != null ? summary.getPendingCount() : 0L);
            length.set(streams.xlen(RedisStreamNames.JOB_RUN));
            dlqLength.set(streams.xlen(RedisStreamNames.JOB_RUN_DLQ));
        } catch (Exception e) {
            log.debug("[STREAM][METRICS] sample failed: {}", e.getMessage());
        }
    }
}