package com.elyadata.feature.job.infrastructure.stream;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class CrawlJoinDispatcher {

    private final StreamCommands<String, String, String> streams;

    public CrawlJoinDispatcher(RedisDataSource redisDataSource) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
    }

    public void publish(UUID jobId, int count) {
        for (int i = 0; i < count; i++) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put(RedisStreamNames.FIELD_JOB_ID, jobId.toString());
            payload.put(RedisStreamNames.FIELD_ENQUEUED_AT, Instant.now().toString());
            streams.xadd(RedisStreamNames.CRAWL_JOIN, payload);
        }
        log.info("[CRAWL-JOIN] published {} join message(s) for job={}", count, jobId);
    }
}