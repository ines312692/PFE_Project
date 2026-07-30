package com.elyadata.feature.job.domain.service;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.elyadata.common.config.SessionCrypto;
import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.job.domain.service.webhook.WebhookCredentialGenerator;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.service.port.ProxyConfigPort;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.elyadata.feature.scraper.model.FieldConfig;
import com.elyadata.feature.scraper.model.FieldType;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.pipeline.ScrapeEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import jakarta.enterprise.context.control.ActivateRequestContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private static final Set<JobStatus> LAUNCHABLE_STATUSES =
            Set.of(JobStatus.CREATED, JobStatus.PAUSED, JobStatus.WAITING_FOR_HUMAN,
                    JobStatus.COMPLETED, JobStatus.FAILED);
    private static final Set<JobStatus> STREAM_RUNNABLE =
            Set.of(JobStatus.CREATED, JobStatus.QUEUED, JobStatus.RUNNING,
                    JobStatus.PAUSED, JobStatus.WAITING_FOR_HUMAN,
                    JobStatus.COMPLETED, JobStatus.FAILED);

    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_DELAY_MS = 1_000;
    private static final int DEFAULT_MAX_PAGES = 50;
    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int DEFAULT_MAX_PAGINATION = 10;
    private final SessionCrypto sessionCrypto;

    private static final CronParser CRON_PARSER =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    private final JobPort jobPort;
    private final JobScheduler jobScheduler;
    private final ExtractionSchemaPort schemaPort;
    private final ProxyConfigPort proxyConfigPort;
    private final ScrapeEngine scrapeEngine;
    private final ManagedExecutor managedExecutor;
    private final WebhookCredentialGenerator webhookGenerator;


    @Override
    public Job createJob(Job job, UUID createdBy) {
        Job sanitized = enforceCrawlerExtractionRules(job);
        validateCronIfScheduled(sanitized);

        Job persisted = persistNewJob(sanitized, createdBy);
        safeSchedule(persisted);
        return persisted;
    }

    @Transactional
    protected Job persistNewJob(Job sanitized, UUID createdBy) {
        Job toCreate = applyWebhookOnCreate(Job.builder()
                .name(sanitized.name())
                .targetUrl(sanitized.targetUrl())
                .jobType(sanitized.jobType())
                .status(JobStatus.CREATED)
                .priority(sanitized.priority())
                .triggerType(sanitized.triggerType())
                .extractionMode(sanitized.extractionMode())
                .browserConfig(sanitized.browserConfig())
                .retryPolicy(sanitized.retryPolicy())
                .scraperConfig(sanitized.scraperConfig())
                .crawlerConfig(sanitized.crawlerConfig())
                .cronExpression(sanitized.cronExpression())
                .projectId(sanitized.projectId())
                .createdBy(createdBy)
                .build());


        Job created = jobPort.save(toCreate);
        log.info("[JOB] Created id={} name={} type={} mode={} projectId={} by={}",
                created.id(), created.name(), created.jobType(),
                created.extractionMode(), created.projectId(), createdBy);

        Job withSchema = saveSchemaIfPresent(created, sanitized.extractionSchema());
        return saveProxyConfig(withSchema, sanitized.proxyConfig());
    }


    @Override
    public Page<Job> search(JobSearchCriteria criteria, int page, int size) {
        Page<Job> raw = jobPort.search(criteria, page, size);
        return raw.map(this::enrich);
    }

    @Override
    public JobStats getStats(UUID ownerId) {
        Map<JobStatus, Long> counts = jobPort.countByStatus(ownerId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return JobStats.builder()
                .total(total)
                .created(counts.getOrDefault(JobStatus.CREATED, 0L))
                .queued(counts.getOrDefault(JobStatus.QUEUED, 0L))
                .running(counts.getOrDefault(JobStatus.RUNNING, 0L))
                .paused(counts.getOrDefault(JobStatus.PAUSED, 0L))
                .completed(counts.getOrDefault(JobStatus.COMPLETED, 0L))
                .failed(counts.getOrDefault(JobStatus.FAILED, 0L))
                .cancelled(counts.getOrDefault(JobStatus.CANCELLED, 0L))
                .build();
    }

    @Override
    public List<Job> getAllJobs() {
        return enrichAll(jobPort.findAll());
    }

    @Override
    public List<Job> getAllJobsByUser(UUID userId) {
        return enrichAll(jobPort.findAllByCreatedBy(userId));
    }

    @Override
    public List<Job> getJobsByProjectId(UUID projectId) {
        return enrichAll(jobPort.findAllByProjectId(projectId));
    }

    @Override
    public Job getJobById(UUID id) {
        return enrich(findJobOrThrow(id));
    }

    @Override
    public Job getJobByIdAndUser(UUID id, UUID userId) {
        return enrich(jobPort.findByIdAndCreatedBy(id, userId)
                .orElseThrow(() -> DomainException.notFound("Job not found: " + id)));
    }


    @Override
    public Job updateJob(UUID id, Job job, UUID updatedBy) {
        Job existing = getJobById(id);
        Job sanitized = enforceCrawlerExtractionRules(job);
        validateCronIfScheduled(sanitized);

        Job persisted = persistUpdatedJob(id, sanitized, existing, updatedBy);
        safeReschedule(persisted);
        return persisted;
    }

    @Transactional
    protected Job persistUpdatedJob(UUID id, Job sanitized, Job existing, UUID updatedBy) {
        Job updated = jobPort.update(Job.builder()
                .id(id)
                .name(sanitized.name())
                .targetUrl(sanitized.targetUrl())
                .jobType(sanitized.jobType())
                .status(existing.status())
                .priority(sanitized.priority())
                .triggerType(sanitized.triggerType())
                .extractionMode(sanitized.extractionMode())
                .browserConfig(sanitized.browserConfig())
                .retryPolicy(sanitized.retryPolicy())
                .scraperConfig(sanitized.scraperConfig())
                .crawlerConfig(sanitized.crawlerConfig())
                .cronExpression(sanitized.cronExpression())
                .projectId(sanitized.projectId() != null ? sanitized.projectId() : existing.projectId())
                .createdBy(existing.createdBy())
                .updatedBy(updatedBy)
                .build());

        log.info("[JOB] Updated id={} type={} mode={} by={}",
                id, sanitized.jobType(), sanitized.extractionMode(), updatedBy);

        if (sanitized.jobType() == JobType.CRAWLER) {
            schemaPort.deleteByJobId(id);
        } else {
            updated = saveSchemaIfPresent(updated, sanitized.extractionSchema());
        }

        return saveProxyConfig(updated, sanitized.proxyConfig());
    }


    @Override
    public void deleteJob(UUID id) {
        if (jobPort.findById(id).isEmpty()) {
            log.debug("[JOB] Delete called on non-existent id={}, ignoring (idempotent)", id);
            return;
        }
        deleteJobTx(id);
        // Unschedule AFTER commit
        safeUnschedule(id);
    }

    @Transactional
    protected void deleteJobTx(UUID id) {
        schemaPort.deleteByJobId(id);
        proxyConfigPort.deleteByJobId(id);
        jobPort.deleteById(id);
        log.info("[JOB] Deleted id={}", id);
    }


    @Override
    public Job duplicateJob(UUID id, UUID createdBy) {
        Job existing = getJobById(id);
        return createJob(Job.builder()
                .name(existing.name() + " (copy)")
                .targetUrl(existing.targetUrl())
                .jobType(existing.jobType())
                .status(JobStatus.CREATED)
                .priority(existing.priority())
                .triggerType(JobTriggerType.MANUAL)
                .cronExpression(null)
                .extractionMode(existing.extractionMode())
                .browserConfig(existing.browserConfig())
                .retryPolicy(existing.retryPolicy())
                .scraperConfig(existing.scraperConfig())
                .crawlerConfig(existing.crawlerConfig())
                .extractionSchema(existing.extractionSchema())
                .proxyConfig(existing.proxyConfig())
                .projectId(existing.projectId())
                .createdBy(createdBy)
                .build(), createdBy);
    }

    @Override
    public Job launchJob(UUID id) {
        Job hydrated = transitionToRunning(id);

        log.info("[JOB] Launched id={} type={} proxy={}",
                id, hydrated.jobType(), describeProxyConfig(hydrated.proxyConfig()));

        InternalScrapeRequest request = buildRequest(hydrated);
        managedExecutor.runAsync(() -> {
            log.info("[JOB] Submitting scrape for job={}", id);
            try {
                scrapeEngine.runScrape(request);
            } catch (Exception e) {
                log.error("[JOB] ScrapeEngine error for job={}", id, e);
            }
        });

        return hydrated;
    }

    @Transactional
    protected Job transitionToRunning(UUID id) {
        Job job = getJobById(id);
        if (!LAUNCHABLE_STATUSES.contains(job.status())) {
            throw DomainException.notValid(
                    "Job " + id + " cannot be launched from status " + job.status());
        }
        jobPort.update(withStatus(job, JobStatus.RUNNING));
        return getJobById(id);
    }

    @Override
    public Job pauseJob(UUID id) {
        Job job = getJobById(id);
        if (job.status() != JobStatus.RUNNING) {
            throw DomainException.notValid(
                    "Job " + id + " can only be paused when RUNNING, current: " + job.status());
        }
        return enrich(transitionToPaused(id));
    }

    @Transactional
    protected Job transitionToPaused(UUID id) {
        Job job = getJobById(id);
        return jobPort.update(withStatus(job, JobStatus.PAUSED));
    }


    private void safeSchedule(Job job) {
        try {
            jobScheduler.schedule(job);
        } catch (Exception e) {
            log.error("[JOB] Scheduling failed for job={} — will retry on next boot",
                    job.id(), e);
        }
    }

    private void safeReschedule(Job job) {
        try {
            jobScheduler.reschedule(job);
        } catch (Exception e) {
            log.error("[JOB] Rescheduling failed for job={} — will retry on next boot",
                    job.id(), e);
        }
    }

    private void safeUnschedule(UUID jobId) {
        try {
            jobScheduler.unschedule(jobId);
        } catch (Exception e) {
            log.error("[JOB] Unschedule failed for job={} — orphan trigger may remain",
                    jobId, e);
        }
    }

    private static void validateCronIfScheduled(Job job) {
        if (job.triggerType() != JobTriggerType.SCHEDULED) return;
        String cron = job.cronExpression();
        if (cron == null || cron.isBlank()) {
            throw DomainException.notValid("Cron expression is required for SCHEDULED jobs");
        }
        try {
            CRON_PARSER.parse(cron.trim()).validate();
        } catch (Exception e) {
            throw DomainException.notValid("Invalid cron expression: " + cron);
        }
    }


    private InternalScrapeRequest buildRequest(Job job) {
        InternalScrapeRequest.InternalScrapeRequestBuilder builder = InternalScrapeRequest.builder()
                .jobId(job.id())
                .targetUrl(job.targetUrl())
                .jobType(Objects.requireNonNullElse(job.jobType(), JobType.SCRAPER))
                .extractionMode(resolveExecutionMode(job.extractionMode()))
                .retryPolicy(job.retryPolicy())
                .proxyConfig(resolveProxyConfig(job.proxyConfig()));

        applyBrowserConfig(builder, job.browserConfig());
        applyScraperConfig(builder, job.scraperConfig());
        applyCrawlerConfig(builder, job.crawlerConfig());
        applyPaginationConfig(builder, job);
        applyExtractionData(builder, job.extractionSchema(), job.scraperConfig());

        log.info("[JOB] buildRequest — job.browserConfig.sessionConfig present={}",
                job.browserConfig() != null && job.browserConfig().sessionConfig() != null);

        return builder.build();
    }

    private static void applyBrowserConfig(
            InternalScrapeRequest.InternalScrapeRequestBuilder builder,
            BrowserConfig cfg) {
        if (cfg != null && cfg.sessionConfig() != null) {
            SessionConfig sc = cfg.sessionConfig();
            log.info("[JOB] buildRequest sessionConfig — enabled={} mode={} usable={} loginUrl={}",
                    sc.enabled(), sc.mode(), sc.isUsable(), sc.loginUrl());
        } else {
            log.info("[JOB] buildRequest sessionConfig — NULL");
        }
        builder
                .jsEnabled(resolveBoolean(cfg != null ? cfg.javascriptEnabled() : null))
                .stealthMode(cfg != null && Boolean.TRUE.equals(cfg.stealthMode()))
                .timeoutMs(resolvePositive(cfg != null ? cfg.timeoutMs() : null, DEFAULT_TIMEOUT_MS))
                .delayMs(resolvePositive(cfg != null ? cfg.requestDelayMs() : null, DEFAULT_DELAY_MS))
                .userAgent(cfg != null ? cfg.userAgent() : null)
                .sessionConfig(cfg != null ? cfg.sessionConfig() : null);
    }
    private static void applyScraperConfig(
            InternalScrapeRequest.InternalScrapeRequestBuilder builder,
            ScraperConfig cfg) {
        builder
                .waitForSelector(cfg != null ? cfg.waitForSelector() : null)
                .scrollToBottom(cfg != null && Boolean.TRUE.equals(cfg.scrollToBottom()))
                .clickBefore(cfg != null ? cfg.clickBeforeExtract() : null)
                .singlePageMode(cfg == null || Boolean.TRUE.equals(cfg.singlePageMode()))
                .seedUrls(cfg != null ? cfg.seedUrls() : null)
                .preExtractActions(
                        cfg != null && cfg.preExtractActions() != null
                                ? cfg.preExtractActions()
                                : List.of());
    }

    private static void applyCrawlerConfig(
            InternalScrapeRequest.InternalScrapeRequestBuilder builder,
            CrawlerConfig cfg) {
        builder
                .maxPages(resolvePositive(cfg != null ? cfg.maxPages() : null, DEFAULT_MAX_PAGES))
                .maxDepth(resolvePositive(cfg != null ? cfg.maxDepth() : null, DEFAULT_MAX_DEPTH))
                .urlIncludePatterns(cfg != null ? cfg.urlIncludePatterns() : null)
                .urlExcludePatterns(cfg != null ? cfg.urlExcludePatterns() : null)
                .sitemapUrl(cfg != null ? cfg.sitemapUrl() : null)
                .followRedirects(cfg == null || !Boolean.FALSE.equals(cfg.followRedirects()))
                .respectRobotsTxt(cfg == null || !Boolean.FALSE.equals(cfg.respectRobotsTxt()));
    }

    private static void applyPaginationConfig(
            InternalScrapeRequest.InternalScrapeRequestBuilder builder,
            Job job) {
        if (job.jobType() == JobType.SCRAPER) {
            ScraperConfig sc = job.scraperConfig();
            if (sc != null) {
                String paginationType = sc.paginationType() != null ? sc.paginationType().name() : null;
                log.debug("[JOB] Scraper pagination — type={} maxPages={} selector={}",
                        paginationType, sc.maxPaginationPages(), sc.paginationSelector());
                builder
                        .paginationType(paginationType)
                        .paginationSelector(sc.paginationSelector())
                        .maxPaginationPages(resolvePositive(sc.maxPaginationPages(), DEFAULT_MAX_PAGINATION));
            } else {
                builder
                        .paginationType(null)
                        .paginationSelector(null)
                        .maxPaginationPages(DEFAULT_MAX_PAGINATION);
            }
        }
    }

    private void applyExtractionData(
            InternalScrapeRequest.InternalScrapeRequestBuilder builder,
            ExtractionSchema schema,
            ScraperConfig scraperConfig) {
        builder
                .fields(resolveFields(schema))
                .llmExtractionPrompt(resolveLlmPrompt(schema, scraperConfig))
                .llmModel(resolveLlmModel(schema))
                .llmOutputFormat(schema != null ? schema.llmOutputFormat() : null)
                .regexPattern(schema != null ? schema.regexPattern() : null)
                .regexGroup(schema != null ? schema.regexGroup() : null);
    }


    private Job saveProxyConfig(Job job, ProxyConfig config) {
        ProxyConfig toSave = resolveProxyConfig(config);

        log.debug("[JOB] Saving proxy config for job={} — enabled={} strategy={}",
                job.id(),
                toSave.isEnabled(),
                toSave.isEnabled() ? toSave.rotationStrategy() : "DIRECT_IP");

        ProxyConfig saved = proxyConfigPort.saveOrUpdate(job.id(), toSave);
        return job.toBuilder().proxyConfig(saved).build();
    }

    private static ProxyConfig resolveProxyConfig(ProxyConfig config) {
        if (config == null) {
            return ProxyConfig.disabled();
        }
        if (config.enabled() == null) {
            log.warn("[JOB] ProxyConfig.enabled is null — forcing to false (direct IP)");
            return config.toBuilder().enabled(false).build();
        }
        return config;
    }

    private Job saveSchemaIfPresent(Job job, ExtractionSchema schema) {
        if (schema == null) return job;
        if (job.jobType() == JobType.CRAWLER) return job;

        ExtractionSchema saved = schemaPort.saveOrUpdate(ExtractionSchema.builder()
                .jobId(job.id())
                .fields(schema.fields())
                .llmPrompt(schema.llmPrompt())
                .llmModel(schema.llmModel())
                .llmOutputFormat(schema.llmOutputFormat())
                .regexPattern(schema.regexPattern())
                .regexGroup(schema.regexGroup())
                .build());
        return withSchema(job, saved);
    }

    private Job enrich(Job job) {
        return enrichWithProxyConfig(enrichWithSchema(job));
    }

    private List<Job> enrichAll(List<Job> jobs) {
        return jobs.stream().map(this::enrich).toList();
    }

    private Job enrichWithSchema(Job job) {
        if (job.jobType() == JobType.CRAWLER) return job;
        return schemaPort.findByJobId(job.id())
                .map(s -> withSchema(job, s))
                .orElse(job);
    }

    private Job enrichWithProxyConfig(Job job) {
        return proxyConfigPort.findByJobId(job.id())
                .map(c -> job.toBuilder().proxyConfig(c).build())
                .orElseGet(() -> {
                    log.debug("[JOB] No proxy config in DB for job={} — defaulting to disabled", job.id());
                    return job.toBuilder().proxyConfig(ProxyConfig.disabled()).build();
                });
    }

    private Job findJobOrThrow(UUID id) {
        return jobPort.findById(id)
                .orElseThrow(() -> DomainException.notFound("Job not found: " + id));
    }

    private static int resolvePositive(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private static boolean resolveBoolean(Boolean value) {
        return Objects.requireNonNullElse(value, true);
    }

    private static String resolveLlmPrompt(ExtractionSchema schema, ScraperConfig scraperConfig) {
        if (schema != null && schema.llmPrompt() != null) return schema.llmPrompt();
        return scraperConfig != null ? scraperConfig.llmExtractionPrompt() : null;
    }

    private static String resolveLlmModel(ExtractionSchema schema) {
        if (schema == null || schema.llmModel() == null) return null;
        return schema.llmModel().name();
    }

    private List<FieldConfig> resolveFields(ExtractionSchema schema) {
        if (schema == null || schema.fields() == null) return List.of();
        return schema.fields().stream().map(this::toFieldConfig).toList();
    }

    private FieldConfig toFieldConfig(com.elyadata.feature.schema.domain.model.SchemaField f) {
        return FieldConfig.builder()
                .name(f.name())
                .selector(f.selector())
                .type(f.type() != null ? FieldType.valueOf(f.type().name()) : FieldType.TEXT)
                .attribute(f.attribute())
                .required(f.required())
                .build();
    }

    private Job enforceCrawlerExtractionRules(Job job) {
        if (job == null || job.jobType() != JobType.CRAWLER) return job;
        if (job.extractionMode() == ExtractionMode.AUTO && job.extractionSchema() == null) return job;

        log.debug("[JOB] Overriding extraction settings for CRAWLER job name={} (mode {} → AUTO)",
                job.name(), job.extractionMode());
        return job.toBuilder()
                .extractionMode(ExtractionMode.AUTO)
                .extractionSchema(null)
                .build();
    }

    private Job withSchema(Job job, ExtractionSchema schema) {
        return job.toBuilder().extractionSchema(schema).build();
    }

    private Job applyWebhookOnCreate(Job job) {
        if (job.triggerType() != JobTriggerType.WEBHOOK) {
            return job.toBuilder()
                    .webhookEnabled(false)
                    .webhookToken(null)
                    .webhookSecret(null)
                    .build();
        }
        if (job.webhookToken() != null && job.webhookSecret() != null) {
            return job;
        }
        var creds = webhookGenerator.generate();
        log.info("[JOB] Generated webhook credentials for new job (token={}…)",
                creds.token().substring(0, 8));
        return job.toBuilder()
                .webhookEnabled(true)
                .webhookToken(creds.token())
                .webhookSecret(creds.secret())
                .build();
    }

    @Transactional
    public WebhookCredentials rotateWebhookSecret(UUID jobId) {
        Job job = findJobOrThrow(jobId);
        if (job.triggerType() != JobTriggerType.WEBHOOK) {
            throw DomainException.notValid("Job " + jobId + " is not webhook-triggered");
        }
        String token = job.webhookToken() != null
                ? job.webhookToken()
                : webhookGenerator.generate().token();
        WebhookCredentials creds = webhookGenerator.regenerateSecret(token);
        jobPort.updateWebhookCredentials(jobId, creds);
        log.info("[JOB] Rotated webhook secret for job={}", jobId);
        return creds;
    }

    @Transactional
    public void disableWebhook(UUID jobId) {
        Job job = findJobOrThrow(jobId);
        jobPort.updateWebhookCredentials(jobId, WebhookCredentials.builder()
                .token(job.webhookToken())
                .secret(null)
                .enabled(false)
                .build());
        log.info("[JOB] Disabled webhook for job={}", jobId);
    }

    private static ExtractionMode resolveExecutionMode(ExtractionMode mode) {
        if (mode == null || mode == ExtractionMode.VISUAL) {
            return ExtractionMode.CSS_SELECTOR;
        }
        return mode;
    }


    private Job withStatus(Job job, JobStatus status) {
        return job.toBuilder().status(status).build();
    }

    private static String describeProxyConfig(ProxyConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) return "disabled(direct-IP)";
        return "enabled(" + cfg.rotationStrategy() + ")";
    }

    @Override
    public Job runJobSync(UUID id) {
        Job hydrated = transitionToRunningFromStream(id);

        log.info("[JOB] Running (sync) id={} type={} proxy={}",
                id, hydrated.jobType(), describeProxyConfig(hydrated.proxyConfig()));

        InternalScrapeRequest request = buildRequest(hydrated);
        scrapeEngine.runScrape(request);
        return hydrated;
    }

    @Transactional
    protected Job transitionToRunningFromStream(UUID id) {
        Job job = getJobById(id);
        if (!STREAM_RUNNABLE.contains(job.status())) {
            throw DomainException.notValid(
                    "Job " + id + " cannot run from status " + job.status());
        }
        jobPort.update(withStatus(job, JobStatus.RUNNING));
        return getJobById(id);
    }

    @Override
    @ActivateRequestContext
    public InternalScrapeRequest buildRequestForJoin(UUID jobId) {
        Job hydrated = getJobById(jobId);
        log.info("[JOB] buildRequestForJoin job={} type={}", jobId, hydrated.jobType());
        return buildRequest(hydrated);
    }
}