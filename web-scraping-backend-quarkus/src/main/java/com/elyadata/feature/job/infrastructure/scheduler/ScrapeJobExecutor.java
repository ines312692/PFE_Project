package com.elyadata.feature.job.infrastructure.scheduler;

import com.elyadata.feature.job.domain.service.JobEnqueueService;
import io.quarkus.arc.Arc;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.util.UUID;

@Slf4j
public class ScrapeJobExecutor implements Job {

    public static final String JOB_ID_KEY = "jobId";

    @Override
    public void execute(JobExecutionContext context) {
        String raw = context.getJobDetail().getJobDataMap().getString(JOB_ID_KEY);
        if (raw == null || raw.isBlank()) {
            log.warn("[SCHED] Skipping fire — no jobId in JobDataMap");
            return;
        }
        UUID jobId = UUID.fromString(raw);
        try {
            Arc.container().instance(JobEnqueueService.class).get().enqueue(jobId, "cron");
        } catch (Exception e) {
            log.error("[SCHED] enqueue failed for job={}", jobId, e);
        }
    }
}