package com.elyadata.feature.job.domain.service.port;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobSearchCriteria;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.WebhookCredentials;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface JobPort {

    Job save(Job job);

    Job update(Job job);

    Optional<Job> findById(UUID id);

    Optional<Job> findByIdAndCreatedBy(UUID id, UUID createdBy);

    Page<Job> search(JobSearchCriteria criteria, int page, int size);

    Map<JobStatus, Long> countByStatus(UUID ownerId);

    List<Job> findAll();

    List<Job> findAllByCreatedBy(UUID createdBy);

    List<Job> findAllByProjectId(UUID projectId);

    void deleteById(UUID id);

    Optional<Job> findByWebhookToken(String token);

    Job updateWebhookCredentials(UUID jobId,
                                 WebhookCredentials credentials);

}