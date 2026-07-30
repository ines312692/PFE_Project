package com.elyadata.feature.job.infrastructure.adapter;

import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.service.port.JobReconciliationPort;
import com.elyadata.feature.job.infrastructure.repository.JobRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@RequiredArgsConstructor
public class JobReconciliationAdapter implements JobReconciliationPort {

    private final JobRepository repository;

    @Override
    @Transactional
    public List<StaleJob> findStale(Set<JobStatus> statuses, Instant updatedBefore, int limit) {
        if (statuses == null || statuses.isEmpty() || updatedBefore == null || limit <= 0) {
            return List.of();
        }
        return repository.findStaleByStatus(statuses, updatedBefore, limit).stream()
                .map(e -> new StaleJob(e.getId(), e.getStatus(), e.getUpdatedAt()))
                .toList();
    }
}
