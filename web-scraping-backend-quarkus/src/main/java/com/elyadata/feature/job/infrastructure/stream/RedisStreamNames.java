package com.elyadata.feature.job.infrastructure.stream;

public final class RedisStreamNames {
    private RedisStreamNames() {}

    public static final String JOB_RUN = "stream:job.run";
    public static final String JOB_RUN_DLQ = "stream:job.run.dlq";
    public static final String ETL_PROCESS = "stream:etl.process";
    public static final String ETL_PROCESS_DLQ = "stream:etl.process.dlq";

    public static final String ATTEMPTS_KEY_PREFIX = "stream:attempts:";

    public static final String FIELD_JOB_ID = "jobId";
    public static final String FIELD_ENQUEUED_AT = "enqueuedAt";
    public static final String FIELD_DLQ_REASON = "dlqReason";
    public static final String FIELD_ORIGIN_ID = "originId";

    public static final String CRAWL_JOIN = "stream:crawl.join";
    public static final String CRAWL_JOIN_DLQ = "stream:crawl.join.dlq";
}