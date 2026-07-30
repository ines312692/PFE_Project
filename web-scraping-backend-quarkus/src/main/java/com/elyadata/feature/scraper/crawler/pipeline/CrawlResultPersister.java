package com.elyadata.feature.scraper.crawler.pipeline;

import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.service.port.JobDispatchPort;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.scraper.pipeline.PipelineResult;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CrawlResultPersister {

    private static final String STATUS_COMPLETED = "completed";

    private final ScrapeResultPort scrapeResultPort;
    private final JobPort jobPort;
    private final NotificationService notificationService;
    private final JobDispatchPort jobDispatchPort;

    @Transactional
    public boolean persist(UUID jobId, PipelineResult result) {
        int recordCount = result.records() != null ? result.records().size() : 0;

        scrapeResultPort.save(ScrapeResult.builder()
                .jobId(jobId)
                .status(result.status())
                .records(result.records())
                .errors(result.errors())
                .logs(result.logs())
                .pagesCrawled(result.pagesCrawled())
                .durationMs(result.durationMs())
                .engineUsed(result.engineUsed())
                .scrapedAt(Instant.now())
                .discoveredFiles(result.discoveredFiles() != null ? result.discoveredFiles() : List.of())
                .build());

        jobPort.findById(jobId).ifPresent(job -> {
            updateJobStatus(job, result.status());
            sendNotification(job, result.status(), recordCount, result);
        });

        log.info("[DCRAWL-PERSIST] job={} persisted status={} records={}", jobId, result.status(), recordCount);
        return STATUS_COMPLETED.equals(result.status());
    }

    public void persistAndDispatch(UUID jobId, PipelineResult result) {
        boolean completed = persist(jobId, result);
        if (completed) {
            jobDispatchPort.dispatchEtl(jobId);
            log.info("[DCRAWL-PERSIST] ETL dispatched for job={}", jobId);
        }
    }

    private void updateJobStatus(Job job, String status) {
        JobStatus newStatus = STATUS_COMPLETED.equals(status) ? JobStatus.COMPLETED : JobStatus.FAILED;
        jobPort.update(job.toBuilder().status(newStatus).build());
        log.info("[DCRAWL-PERSIST] job={} status → {}", job.id(), newStatus);
    }

    private void sendNotification(Job job, String status, int recordCount, PipelineResult result) {
        if (job.createdBy() == null) return;
        boolean success = STATUS_COMPLETED.equals(status) && recordCount > 0;
        NotificationType type = success ? NotificationType.JOB_COMPLETED : NotificationType.JOB_FAILED;
        try {
            notificationService.notify(job.createdBy(), job.id(), type,
                    (success ? "Job completed: " : "Job failed: ") + job.name(),
                    success
                            ? recordCount + " records extracted in " + (result.durationMs() / 1000.0) + "s"
                            : result.errors().size() + " error(s) during crawling");
        } catch (Exception e) {
            log.warn("[DCRAWL-PERSIST] notification failed job={}: {}", job.id(), e.getMessage());
        }
    }
}