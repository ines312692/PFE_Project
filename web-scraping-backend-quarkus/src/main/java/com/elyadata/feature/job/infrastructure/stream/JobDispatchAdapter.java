package com.elyadata.feature.job.infrastructure.stream;

import com.elyadata.feature.job.domain.service.port.JobDispatchPort;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.XAddArgs;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class JobDispatchAdapter implements JobDispatchPort {

    private final StreamCommands<String, String, String> streams;
    private final StreamConfig config;

    public JobDispatchAdapter(RedisDataSource redisDataSource, StreamConfig config) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
        this.config = config;
    }

    @Override
    public void dispatch(UUID jobId) {
        String id = streams.xadd(RedisStreamNames.JOB_RUN, trimArgs(config.jobRun().maxlen()), payload(jobId));
        log.info("[STREAM] Dispatched job={} → {} id={}", jobId, RedisStreamNames.JOB_RUN, id);
    }

    @Override
    public void dispatchEtl(UUID jobId) {
        String id = streams.xadd(RedisStreamNames.ETL_PROCESS, trimArgs(config.etl().maxlen()), payload(jobId));
        log.debug("[STREAM] Dispatched ETL job={} → {} id={}", jobId, RedisStreamNames.ETL_PROCESS, id);
    }

    private static Map<String, String> payload(UUID jobId) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put(RedisStreamNames.FIELD_JOB_ID, jobId.toString());
        p.put(RedisStreamNames.FIELD_ENQUEUED_AT, String.valueOf(System.currentTimeMillis()));
        return p;
    }

    private static XAddArgs trimArgs(long maxlen) {
        XAddArgs args = new XAddArgs();
        if (maxlen > 0) args.maxlen(maxlen).nearlyExactTrimming();
        return args;
    }
}