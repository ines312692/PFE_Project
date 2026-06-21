package com.elyadata.feature.job.infrastructure.scheduler;

import com.elyadata.feature.job.domain.service.JobService;
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
        log.info("[SCHED] Cron fired for job={} at={}", jobId, context.getFireTime());

        JobService jobService = Arc.container().instance(JobService.class).get();
        try {
            jobService.launchJob(jobId);
        } catch (Exception e) {
            log.error("[SCHED] launchJob failed for job={} — skipping tick", jobId, e);
        }
    }
}