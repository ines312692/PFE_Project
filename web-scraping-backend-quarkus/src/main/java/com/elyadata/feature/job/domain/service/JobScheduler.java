package com.elyadata.feature.job.domain.service;

import com.elyadata.feature.job.domain.model.Job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobScheduler {
    void schedule(Job job);

    void unschedule(UUID jobId);

    void reschedule(Job job);

    List<Instant> nextFireTimes(String cron, int count);

    List<UpcomingRun> upcomingRunsBetween(Instant from, Instant to);

    record UpcomingRun(UUID jobId, String jobName, Instant fireAt) {
    }
}