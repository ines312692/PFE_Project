package com.elyadata.feature.job.domain.service;

import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.service.port.JobDispatchPort;
import com.elyadata.feature.job.domain.service.port.JobPort;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class JobEnqueueService {

    private static final Set<JobStatus> ENQUEUABLE =
            EnumSet.of(JobStatus.CREATED, JobStatus.PAUSED, JobStatus.WAITING_FOR_HUMAN,
                    JobStatus.COMPLETED, JobStatus.FAILED);

    private final JobPort jobPort;
    private final JobDispatchPort dispatchPort;

    public boolean enqueue(UUID jobId, String trigger) {
        Optional<Job> maybe = jobPort.findById(jobId);
        if (maybe.isEmpty()) {
            log.warn("[ENQUEUE] job={} introuvable — ignoré (trigger={})", jobId, trigger);
            return false;
        }
        Job job = maybe.get();

        if (!ENQUEUABLE.contains(job.status())) {
            log.info("[ENQUEUE] job={} status={} — pas re-enfilé (trigger={})", jobId, job.status(), trigger);
            return false;
        }

        jobPort.update(job.toBuilder().status(JobStatus.QUEUED).build());
        try {
            dispatchPort.dispatch(jobId);
        } catch (Exception e) {
            jobPort.update(job);
            log.error("[ENQUEUE] dispatch échoué job={} — statut restauré à {} (trigger={})",
                    jobId, job.status(), trigger, e);
            throw e;
        }
        log.info("[ENQUEUE] job={} → QUEUED + dispatched (trigger={})", jobId, trigger);
        return true;
    }
}