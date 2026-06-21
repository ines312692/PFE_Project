package com.elyadata.feature.scraper.model;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.job.domain.model.PageAction;
import com.elyadata.feature.job.domain.model.RetryPolicy;
import com.elyadata.feature.job.domain.model.SessionConfig;
import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class InternalScrapeRequest {

    private UUID jobId;
    private String targetUrl;

    @Builder.Default
    private JobType jobType = JobType.SCRAPER;

    @Builder.Default
    private ExtractionMode extractionMode = ExtractionMode.CSS_SELECTOR;

    @Builder.Default
    private int maxPages = 50;

    @Builder.Default
    private int maxDepth = 2;

    @Builder.Default
    private boolean jsEnabled = true;

    @Builder.Default
    private boolean stealthMode = false;

    @Builder.Default
    private int delayMs = 1_000;

    @Builder.Default
    private int timeoutMs = 30_000;

    @Builder.Default
    private boolean singlePageMode = true;

    private String userAgent;

    private RetryPolicy retryPolicy;

    private List<String> seedUrls;
    private String waitForSelector;

    @Builder.Default
    private boolean scrollToBottom = false;

    private String clickBefore;

    @Builder.Default
    private List<PageAction> preExtractActions = List.of();

    @Builder.Default
    private List<FieldConfig> fields = List.of();

    private String llmExtractionPrompt;
    private String llmModel;
    private String llmOutputFormat;
    private String regexPattern;
    private String regexGroup;
    private String rawContent;
    private String urlIncludePatterns;
    private String urlExcludePatterns;
    private String paginationType;
    private String paginationSelector;
    private String restoredStorageState;
    private SessionConfig sessionConfig;
    @Builder.Default
    private int maxPaginationPages = 10;
    @Builder.Default
    private boolean followRedirects = true;
    @Builder.Default
    private boolean respectRobotsTxt = true;
    private String sitemapUrl;
    @Builder.Default
    private boolean sameDomainOnly = true;
    private ProxyConfig proxyConfig;
    private Proxy proxy;

}