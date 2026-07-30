package com.elyadata.feature.job.infrastructure.mapper;

import com.elyadata.common.config.SessionCrypto;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.domain.model.SessionConfig.LoginMode;
import com.elyadata.feature.job.infrastructure.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class JobEntityMapper {

    private final SessionCrypto sessionCrypto;

    public Job toDomain(JobEntity entity,
                        BrowserConfigEntity browserEntity,
                        RetryPolicyEntity retryEntity,
                        ScraperConfigEntity scraperEntity,
                        CrawlerConfigEntity crawlerEntity) {
        return Job.builder()
                .id(entity.getId())
                .name(entity.getName())
                .targetUrl(entity.getTargetUrl())
                .jobType(entity.getJobType())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .triggerType(entity.getTriggerType())
                .extractionMode(entity.getExtractionMode())
                .browserConfig(toBrowserConfig(browserEntity))
                .retryPolicy(toRetryPolicy(retryEntity))
                .scraperConfig(scraperEntity != null ? toScraperConfig(scraperEntity) : null)
                .crawlerConfig(crawlerEntity != null ? toCrawlerConfig(crawlerEntity) : null)
                .cronExpression(entity.getCronExpression())
                .webhookToken(entity.getWebhookToken())
                .webhookSecret(entity.getWebhookSecret())
                .webhookEnabled(entity.isWebhookEnabled())
                .projectId(entity.getProjectId())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public JobEntity toEntity(Job domain) {
        return JobEntity.builder()
                .id(domain.id())
                .name(domain.name())
                .targetUrl(domain.targetUrl())
                .jobType(domain.jobType())
                .status(domain.status())
                .priority(domain.priority())
                .triggerType(domain.triggerType())
                .extractionMode(domain.extractionMode())
                .cronExpression(domain.cronExpression())
                .webhookToken(domain.webhookToken())
                .webhookSecret(domain.webhookSecret())
                .webhookEnabled(Boolean.TRUE.equals(domain.webhookEnabled()))
                .projectId(domain.projectId())
                .createdBy(domain.createdBy())
                .updatedBy(domain.updatedBy())
                .build();
    }

    public BrowserConfigEntity toBrowserConfigEntity(BrowserConfig config, UUID jobId) {
        if (config == null) {
            return BrowserConfigEntity.builder()
                    .jobId(jobId)
                    .javascriptEnabled(true)
                    .stealthMode(false)
                    .requestDelayMs(1500)
                    .timeoutMs(30000)
                    .build();
        }
        BrowserConfigEntity.BrowserConfigEntityBuilder builder = BrowserConfigEntity.builder()
                .jobId(jobId)
                .javascriptEnabled(config.javascriptEnabled())
                .stealthMode(config.stealthMode())
                .requestDelayMs(config.requestDelayMs())
                .timeoutMs(config.timeoutMs())
                .userAgent(config.userAgent());

        applySessionToEntity(builder, config.sessionConfig());
        return builder.build();
    }

    public RetryPolicyEntity toRetryPolicyEntity(RetryPolicy policy, UUID jobId) {
        if (policy == null) {
            return RetryPolicyEntity.builder()
                    .jobId(jobId)
                    .maxRetries(3)
                    .retryInitialDelayMs(1000L)
                    .retryBackoffMultiplier(2.0)
                    .retryMaxDelayMs(30000L)
                    .circuitBreakerEnabled(false)
                    .circuitBreakerThreshold(0.5)
                    .circuitBreakerResetSeconds(60)
                    .build();
        }
        return RetryPolicyEntity.builder()
                .jobId(jobId)
                .maxRetries(policy.maxRetries())
                .retryInitialDelayMs(policy.retryInitialDelayMs() != null ? policy.retryInitialDelayMs().longValue() : null)
                .retryBackoffMultiplier(policy.retryBackoffMultiplier())
                .retryMaxDelayMs(policy.retryMaxDelayMs() != null ? policy.retryMaxDelayMs().longValue() : null)
                .circuitBreakerEnabled(policy.circuitBreakerEnabled())
                .circuitBreakerThreshold(policy.circuitBreakerThreshold())
                .circuitBreakerResetSeconds(policy.circuitBreakerResetSeconds())
                .build();
    }

    public ScraperConfigEntity toScraperConfigEntity(ScraperConfig config, UUID jobId) {
        if (config == null) return null;
        return ScraperConfigEntity.builder()
                .jobId(jobId)
                .seedUrls(config.seedUrls())
                .singlePageMode(config.singlePageMode())
                .waitForSelector(config.waitForSelector())
                .scrollToBottom(config.scrollToBottom())
                .clickBeforeExtract(config.clickBeforeExtract())
                .llmExtractionPrompt(config.llmExtractionPrompt())
                .paginationType(config.paginationType())
                .paginationSelector(config.paginationSelector())
                .maxPaginationPages(config.maxPaginationPages())
                .preExtractActions(config.preExtractActions())
                .build();
    }

    public CrawlerConfigEntity toCrawlerConfigEntity(CrawlerConfig config, UUID jobId) {
        if (config == null) return null;
        return CrawlerConfigEntity.builder()
                .jobId(jobId)
                .maxPages(config.maxPages())
                .maxDepth(config.maxDepth())
                .urlIncludePatterns(config.urlIncludePatterns())
                .urlExcludePatterns(config.urlExcludePatterns())
                .followRedirects(config.followRedirects())
                .respectRobotsTxt(config.respectRobotsTxt())
                .sitemapUrl(config.sitemapUrl())
                .build();
    }

    private BrowserConfig toBrowserConfig(BrowserConfigEntity e) {
        if (e == null) return BrowserConfig.builder().build();
        return BrowserConfig.builder()
                .javascriptEnabled(e.getJavascriptEnabled())
                .stealthMode(e.getStealthMode())
                .requestDelayMs(e.getRequestDelayMs())
                .timeoutMs(e.getTimeoutMs())
                .userAgent(e.getUserAgent())
                .sessionConfig(toSessionConfig(e))
                .build();
    }

    private RetryPolicy toRetryPolicy(RetryPolicyEntity e) {
        if (e == null) return RetryPolicy.builder().build();
        return RetryPolicy.builder()
                .maxRetries(e.getMaxRetries())
                .retryInitialDelayMs(e.getRetryInitialDelayMs() != null ? e.getRetryInitialDelayMs().intValue() : null)
                .retryBackoffMultiplier(e.getRetryBackoffMultiplier())
                .retryMaxDelayMs(e.getRetryMaxDelayMs() != null ? e.getRetryMaxDelayMs().intValue() : null)
                .circuitBreakerEnabled(e.getCircuitBreakerEnabled())
                .circuitBreakerThreshold(e.getCircuitBreakerThreshold())
                .circuitBreakerResetSeconds(e.getCircuitBreakerResetSeconds())
                .build();
    }

    private ScraperConfig toScraperConfig(ScraperConfigEntity e) {
        return ScraperConfig.builder()
                .seedUrls(e.getSeedUrls())
                .singlePageMode(e.getSinglePageMode())
                .waitForSelector(e.getWaitForSelector())
                .scrollToBottom(e.getScrollToBottom())
                .clickBeforeExtract(e.getClickBeforeExtract())
                .llmExtractionPrompt(e.getLlmExtractionPrompt())
                .paginationType(e.getPaginationType())
                .paginationSelector(e.getPaginationSelector())
                .maxPaginationPages(e.getMaxPaginationPages())
                .preExtractActions(e.getPreExtractActions())
                .build();
    }

    private CrawlerConfig toCrawlerConfig(CrawlerConfigEntity e) {
        return CrawlerConfig.builder()
                .maxPages(e.getMaxPages())
                .maxDepth(e.getMaxDepth())
                .urlIncludePatterns(e.getUrlIncludePatterns())
                .urlExcludePatterns(e.getUrlExcludePatterns())
                .followRedirects(e.getFollowRedirects())
                .respectRobotsTxt(e.getRespectRobotsTxt())
                .sitemapUrl(e.getSitemapUrl())
                .build();
    }

    private void applySessionToEntity(BrowserConfigEntity.BrowserConfigEntityBuilder builder,
                                      SessionConfig sc) {
        if (sc == null || !sc.enabled()) {
            log.info("[MAPPER-ENTITY] WRITE — session null or disabled (sc={}, enabled={})",
                    sc != null, false);
            builder.sessionLoginEnabled(false);
            return;
        }
        log.info("[MAPPER-ENTITY] WRITE — enabled={} mode={} loginUrl={} userSel={}",
                true, sc.mode(), sc.loginUrl(), sc.usernameSelector());
        builder
                .sessionLoginEnabled(true)
                .sessionLoginMode(sc.mode() != null ? sc.mode().name() : null)
                .sessionLoginUrl(sc.loginUrl())
                .sessionUsernameSel(sc.usernameSelector())
                .sessionPasswordSel(sc.passwordSelector())
                .sessionSubmitSel(sc.submitSelector())
                .sessionSuccessSel(sc.successSelector())
                .sessionUsernameEnc(encryptIfPresent(sc.usernameEnc()))
                .sessionPasswordEnc(encryptIfPresent(sc.passwordEnc()))
                .sessionImportedState(sc.importedStorageState());
    }


    private SessionConfig toSessionConfig(BrowserConfigEntity e) {
        if (!Boolean.TRUE.equals(e.getSessionLoginEnabled())) {
            return SessionConfig.disabled();
        }
        return SessionConfig.builder()
                .enabled(true)
                .mode(parseMode(e.getSessionLoginMode()))
                .loginUrl(e.getSessionLoginUrl())
                .usernameSelector(e.getSessionUsernameSel())
                .passwordSelector(e.getSessionPasswordSel())
                .submitSelector(e.getSessionSubmitSel())
                .successSelector(e.getSessionSuccessSel())
                .usernameEnc(decryptIfPresent(e.getSessionUsernameEnc()))
                .passwordEnc(decryptIfPresent(e.getSessionPasswordEnc()))
                .importedStorageState(e.getSessionImportedState())
                .build();
    }
    private String decryptIfPresent(String enc) {
        return (enc == null || enc.isBlank()) ? null : sessionCrypto.decrypt(enc);
    }

    private String encryptIfPresent(String cleartext) {
        return (cleartext == null || cleartext.isBlank())
                ? null
                : sessionCrypto.encrypt(cleartext);
    }

    private static LoginMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LoginMode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void applySessionToManagedEntity(BrowserConfigEntity e, SessionConfig sc) {
        if (sc == null || !sc.enabled()) {
            log.info("[MAPPER-ENTITY] UPDATE — session null or disabled");
            e.setSessionLoginEnabled(false);
            e.setSessionLoginMode(null);
            e.setSessionLoginUrl(null);
            e.setSessionUsernameSel(null);
            e.setSessionPasswordSel(null);
            e.setSessionSubmitSel(null);
            e.setSessionSuccessSel(null);
            e.setSessionUsernameEnc(null);
            e.setSessionPasswordEnc(null);
            e.setSessionImportedState(null);
            return;
        }
        log.info("[MAPPER-ENTITY] UPDATE — enabled={} mode={} loginUrl={} userSel={}",
                true, sc.mode(), sc.loginUrl(), sc.usernameSelector());
        e.setSessionLoginEnabled(true);
        e.setSessionLoginMode(sc.mode() != null ? sc.mode().name() : null);
        e.setSessionLoginUrl(sc.loginUrl());
        e.setSessionUsernameSel(sc.usernameSelector());
        e.setSessionPasswordSel(sc.passwordSelector());
        e.setSessionSubmitSel(sc.submitSelector());
        e.setSessionSuccessSel(sc.successSelector());

        if (isPresent(sc.usernameEnc())) {
            e.setSessionUsernameEnc(encryptIfPresent(sc.usernameEnc()));
        }
        if (isPresent(sc.passwordEnc())) {
            e.setSessionPasswordEnc(encryptIfPresent(sc.passwordEnc()));
        }
        if (isPresent(sc.importedStorageState())) {
            e.setSessionImportedState(sc.importedStorageState());
        }
    }

    private static boolean isPresent(String v) {
        return v != null && !v.isBlank();
    }
}