package com.elyadata.feature.job.domain.service;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobSearchCriteria;
import com.elyadata.feature.job.domain.model.JobStats;
import com.elyadata.feature.job.domain.model.WebhookCredentials;

import java.util.List;
import java.util.UUID;

public interface JobService {
    Job createJob(Job job, UUID createdBy);

    Page<Job> search(JobSearchCriteria criteria, int page, int size);

    JobStats getStats(UUID ownerId);

    Job getJobById(UUID id);

    Job getJobByIdAndUser(UUID id, UUID userId);

    Job updateJob(UUID id, Job job, UUID updatedBy);

    void deleteJob(UUID id);

    Job duplicateJob(UUID id, UUID createdBy);

    Job launchJob(UUID id);

    Job pauseJob(UUID id);

    List<Job> getAllJobs();

    List<Job> getAllJobsByUser(UUID userId);

    List<Job> getJobsByProjectId(UUID projectId);

    WebhookCredentials rotateWebhookSecret(java.util.UUID jobId);

    void disableWebhook(java.util.UUID jobId);

}