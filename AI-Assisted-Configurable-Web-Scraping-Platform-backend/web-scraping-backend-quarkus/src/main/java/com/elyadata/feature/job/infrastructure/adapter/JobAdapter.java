package com.elyadata.feature.job.infrastructure.adapter;

import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.job.infrastructure.entity.*;
import com.elyadata.feature.job.infrastructure.mapper.JobEntityMapper;
import com.elyadata.feature.job.infrastructure.repository.BrowserConfigRepository;
import com.elyadata.feature.job.infrastructure.repository.CrawlerConfigRepository;
import com.elyadata.feature.job.infrastructure.repository.JobRepository;
import com.elyadata.feature.job.infrastructure.repository.RetryPolicyRepository;
import com.elyadata.feature.job.infrastructure.repository.ScraperConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.elyadata.common.pagination.Page;

import java.util.Map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Transactional
@ApplicationScoped
@RequiredArgsConstructor
public class JobAdapter implements JobPort {

    private final JobRepository jobRepository;
    private final BrowserConfigRepository browserConfigRepository;
    private final RetryPolicyRepository retryPolicyRepository;
    private final ScraperConfigRepository scraperConfigRepository;
    private final CrawlerConfigRepository crawlerConfigRepository;
    private final JobEntityMapper jobEntityMapper;

    @Override
    public Job save(Job job) {
        var entity = jobEntityMapper.toEntity(job);
        jobRepository.persist(entity);
        var jobId = entity.getId();
        log.info("Job saved with id={} type={}", jobId, entity.getJobType());
        browserConfigRepository.persist(
                jobEntityMapper.toBrowserConfigEntity(job.browserConfig(), jobId));
        retryPolicyRepository.persist(
                jobEntityMapper.toRetryPolicyEntity(job.retryPolicy(), jobId));
        saveTypeConfig(job, jobId);
        return toDomainWithConfigs(jobId);
    }


    @Override
    public Optional<Job> findById(UUID id) {
        return jobRepository.findByIdOptional(id)
                .map(e -> toDomainWithConfigs(e.getId()));
    }

    @Override
    public Optional<Job> findByIdAndCreatedBy(UUID id, UUID createdBy) {
        return jobRepository.findByIdAndCreatedBy(id, createdBy)
                .map(e -> toDomainWithConfigs(e.getId()));
    }

    @Override
    public Page<Job> search(JobSearchCriteria criteria, int page, int size) {
        Page<JobEntity> entityPage = jobRepository.search(criteria, page, size);
        if (entityPage.isEmpty()) {
            return Page.empty(page, size);
        }
        List<Job> domain = toDomainBatch(entityPage.content());
        return new Page<>(domain, entityPage.totalElements(), entityPage.page(), entityPage.size());
    }

    @Override
    public Map<JobStatus, Long> countByStatus(UUID ownerId) {
        return jobRepository.countByStatus(ownerId);
    }

    @Override
    public List<Job> findAll() {
        var entities = jobRepository.listAll();
        return toDomainBatch(entities);
    }

    @Override
    public List<Job> findAllByCreatedBy(UUID createdBy) {
        var entities = jobRepository.findAllByCreatedBy(createdBy);
        return toDomainBatch(entities);
    }

    @Override
    public List<Job> findAllByProjectId(UUID projectId) {
        var entities = jobRepository.findAllByProjectId(projectId);
        return toDomainBatch(entities);
    }

    @Override
    public Job update(Job job) {
        var entity = jobRepository.findByIdOptional(job.id())
                .orElseThrow(() -> new IllegalStateException("Entity not found for id: " + job.id()));
        var jobId = entity.getId();
        entity.setName(job.name());
        entity.setTargetUrl(job.targetUrl());
        entity.setJobType(job.jobType());
        entity.setStatus(job.status());
        entity.setPriority(job.priority());
        entity.setTriggerType(job.triggerType());
        entity.setExtractionMode(job.extractionMode());
        entity.setCronExpression(job.cronExpression());
        entity.setProjectId(job.projectId());
        entity.setUpdatedBy(job.updatedBy());
        updateBrowserConfig(job, jobId);
        updateRetryPolicy(job, jobId);
        updateTypeConfig(job, jobId);

        log.info("Job updated id={} type={}", jobId, job.jobType());
        return toDomainWithConfigs(jobId);
    }

    @Override
    public void deleteById(UUID id) {
        log.info("Deleting job id={}", id);
        scraperConfigRepository.deleteByJobId(id);
        crawlerConfigRepository.deleteByJobId(id);
        browserConfigRepository.deleteByJobId(id);
        retryPolicyRepository.deleteByJobId(id);
        jobRepository.deleteById(id);
    }



    private void saveTypeConfig(Job job, UUID jobId) {
        if (job.jobType() == JobType.SCRAPER && job.scraperConfig() != null) {
            scraperConfigRepository.persist(
                    jobEntityMapper.toScraperConfigEntity(job.scraperConfig(), jobId));
            log.debug("ScraperConfig saved for job={}", jobId);
        } else if (job.jobType() == JobType.CRAWLER && job.crawlerConfig() != null) {
            crawlerConfigRepository.persist(
                    jobEntityMapper.toCrawlerConfigEntity(job.crawlerConfig(), jobId));
            log.debug("CrawlerConfig saved for job={}", jobId);
        }
    }

    private void updateBrowserConfig(Job job, UUID jobId) {
        var existing = browserConfigRepository.findByJobId(jobId);
        if (existing.isPresent()) {
            var e = existing.get();
            var c = job.browserConfig();
            if (c != null) {
                e.setJavascriptEnabled(c.javascriptEnabled());
                e.setStealthMode(c.stealthMode());
                e.setRequestDelayMs(c.requestDelayMs());
                e.setTimeoutMs(c.timeoutMs());
                e.setUserAgent(c.userAgent());
                jobEntityMapper.applySessionToManagedEntity(e, c.sessionConfig());
            }
        } else {
            browserConfigRepository.persist(
                    jobEntityMapper.toBrowserConfigEntity(job.browserConfig(), jobId));
        }
    }


    private void updateRetryPolicy(Job job, UUID jobId) {
        var existing = retryPolicyRepository.findByJobId(jobId);
        if (existing.isPresent()) {
            var e = existing.get();
            var r = job.retryPolicy();
            if (r != null) {
                e.setMaxRetries(r.maxRetries());
                e.setRetryInitialDelayMs(r.retryInitialDelayMs() != null ? r.retryInitialDelayMs().longValue() : null);
                e.setRetryBackoffMultiplier(r.retryBackoffMultiplier());
                e.setRetryMaxDelayMs(r.retryMaxDelayMs() != null ? r.retryMaxDelayMs().longValue() : null);
                e.setCircuitBreakerEnabled(r.circuitBreakerEnabled());
                e.setCircuitBreakerThreshold(r.circuitBreakerThreshold());
                e.setCircuitBreakerResetSeconds(r.circuitBreakerResetSeconds());
            }
        } else {
            retryPolicyRepository.persist(
                    jobEntityMapper.toRetryPolicyEntity(job.retryPolicy(), jobId));
        }
    }

    private void updateTypeConfig(Job job, UUID jobId) {
        if (job.jobType() == JobType.SCRAPER) {
            crawlerConfigRepository.deleteByJobId(jobId);
            if (job.scraperConfig() != null) {
                var existing = scraperConfigRepository.findByJobId(jobId);
                if (existing.isPresent()) {
                    var e = existing.get();
                    e.setSeedUrls(job.scraperConfig().seedUrls());
                    e.setSinglePageMode(job.scraperConfig().singlePageMode());
                    e.setWaitForSelector(job.scraperConfig().waitForSelector());
                    e.setScrollToBottom(job.scraperConfig().scrollToBottom());
                    e.setClickBeforeExtract(job.scraperConfig().clickBeforeExtract());
                    e.setLlmExtractionPrompt(job.scraperConfig().llmExtractionPrompt());
                    e.setPaginationType(job.scraperConfig().paginationType());
                    e.setPaginationSelector(job.scraperConfig().paginationSelector());
                    e.setMaxPaginationPages(job.scraperConfig().maxPaginationPages());
                } else {
                    scraperConfigRepository.persist(
                            jobEntityMapper.toScraperConfigEntity(job.scraperConfig(), jobId));
                }
            }
        } else if (job.jobType() == JobType.CRAWLER) {
            scraperConfigRepository.deleteByJobId(jobId);
            if (job.crawlerConfig() != null) {
                var existing = crawlerConfigRepository.findByJobId(jobId);
                if (existing.isPresent()) {
                    var e = existing.get();
                    e.setMaxPages(job.crawlerConfig().maxPages());
                    e.setMaxDepth(job.crawlerConfig().maxDepth());
                    e.setUrlIncludePatterns(job.crawlerConfig().urlIncludePatterns());
                    e.setUrlExcludePatterns(job.crawlerConfig().urlExcludePatterns());
                    e.setFollowRedirects(job.crawlerConfig().followRedirects());
                    e.setRespectRobotsTxt(job.crawlerConfig().respectRobotsTxt());
                    e.setSitemapUrl(job.crawlerConfig().sitemapUrl());
                } else {
                    crawlerConfigRepository.persist(
                            jobEntityMapper.toCrawlerConfigEntity(job.crawlerConfig(), jobId));
                }
            }
        }
    }

    private Job toDomainWithConfigs(UUID jobId) {
        var entity = jobRepository.findByIdOptional(jobId)
                .orElseThrow(() -> new IllegalStateException("Entity not found for id: " + jobId));
        BrowserConfigEntity browserEntity = browserConfigRepository.findByJobId(jobId).orElse(null);
        RetryPolicyEntity retryEntity = retryPolicyRepository.findByJobId(jobId).orElse(null);
        ScraperConfigEntity scraperEntity = null;
        CrawlerConfigEntity crawlerEntity = null;

        if (entity.getJobType() == JobType.SCRAPER) {
            scraperEntity = scraperConfigRepository.findByJobId(jobId).orElse(null);
        } else if (entity.getJobType() == JobType.CRAWLER) {
            crawlerEntity = crawlerConfigRepository.findByJobId(jobId).orElse(null);
        }

        return jobEntityMapper.toDomain(entity, browserEntity, retryEntity, scraperEntity, crawlerEntity);
    }

    private List<Job> toDomainBatch(List<JobEntity> entities) {
        if (entities.isEmpty()) return List.of();

        List<UUID> jobIds = entities.stream().map(JobEntity::getId).toList();
        var browserConfigs = browserConfigRepository.findByJobIds(jobIds);
        var retryPolicies = retryPolicyRepository.findByJobIds(jobIds);
        var scraperConfigs = scraperConfigRepository.findByJobIds(jobIds);
        var crawlerConfigs = crawlerConfigRepository.findByJobIds(jobIds);

        return entities.stream().map(entity -> {
            UUID id = entity.getId();
            ScraperConfigEntity scraperEntity = null;
            CrawlerConfigEntity crawlerEntity = null;

            if (entity.getJobType() == JobType.SCRAPER) {
                scraperEntity = scraperConfigs.get(id);
            } else if (entity.getJobType() == JobType.CRAWLER) {
                crawlerEntity = crawlerConfigs.get(id);
            }

            return jobEntityMapper.toDomain(
                    entity,
                    browserConfigs.get(id),
                    retryPolicies.get(id),
                    scraperEntity,
                    crawlerEntity
            );
        }).toList();
    }


    @Override
    public Optional<Job> findByWebhookToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return jobRepository.findByWebhookToken(token)
                .map(e -> toDomainWithConfigs(e.getId()));
    }

    @Override
    public Job updateWebhookCredentials(UUID jobId, WebhookCredentials credentials) {
        var entity = jobRepository.findByIdOptional(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        entity.setWebhookToken(credentials.token());
        entity.setWebhookSecret(credentials.secret());
        entity.setWebhookEnabled(credentials.enabled());

        log.info("[JOB] Webhook credentials updated for job={} enabled={}", jobId, credentials.enabled());
        return toDomainWithConfigs(jobId);
    }

}