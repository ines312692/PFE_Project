package com.elyadata.feature.job.domain.model;

public enum JobStatus {
    CREATED,
    QUEUED,
    RUNNING,
    PAUSED,
    WAITING_FOR_HUMAN,
    COMPLETED,
    FAILED,
    CANCELLED
}