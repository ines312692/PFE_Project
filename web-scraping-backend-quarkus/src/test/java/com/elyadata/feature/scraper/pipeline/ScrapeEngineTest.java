package com.elyadata.feature.scraper.pipeline;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.job.domain.service.SiteProbeService;
import com.elyadata.feature.job.domain.service.port.JobDispatchPort;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.model.ProxyOutcome;
import com.elyadata.feature.proxy.domain.service.ProxyPoolService;
import com.elyadata.feature.schema.domain.service.LlmExtractionService;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.elyadata.feature.scraper.antibot.captcha.orchestration.CaptchaHandler;
import com.elyadata.feature.scraper.browser.BrowserFactory;
import com.elyadata.feature.scraper.crawler.pagination.PaginationStrategyRegistry;
import com.elyadata.feature.scraper.crawler.pipeline.DistributedCrawlCoordinator;
import com.elyadata.feature.scraper.crawler.robots.RobotsTxtGate;
import com.elyadata.feature.scraper.extractor.ExtractorRegistry;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.resilience.CircuitBreakerRegistry;
import com.elyadata.feature.scraper.security.SsrfGuard;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScrapeEngine")
class ScrapeEngineTest {

    @Mock BrowserFactory browserFactory;
    @Mock ExtractorRegistry extractorRegistry;
    @Mock ScrapeResultPort scrapeResultPort;
    @Mock JobPort jobPort;
    @Mock NotificationService notificationService;
    @Mock ExtractionSchemaPort schemaPort;
    @Mock LlmExtractionService llmExtractionService;
    @Mock SiteProbeService siteProbeService;
    @Mock CircuitBreakerRegistry cbRegistry;
    @Mock RobotsTxtGate robotsGate;
    @Mock PaginationStrategyRegistry paginationRegistry;
    @Mock ProxyPoolService proxyPoolService;
    @Mock CaptchaHandler captchaHandler;
    @Mock SsrfGuard ssrfGuard;
    @Mock DistributedCrawlCoordinator distributedCrawlCoordinator;
    @Mock
    JobDispatchPort jobDispatchPort;

    private ScrapeEngine scrapeEngine;

    private static final UUID JOB_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() throws Exception {
        scrapeEngine = new ScrapeEngine(
                jobDispatchPort,
                browserFactory, extractorRegistry, scrapeResultPort, jobPort,
                notificationService, schemaPort, llmExtractionService, siteProbeService,
                cbRegistry, robotsGate, paginationRegistry, proxyPoolService,
                captchaHandler, ssrfGuard, distributedCrawlCoordinator);

        setField("maxProxyRotations", 3);
        setField("cloudflareDomains", List.of("carrefour.fr", "carrefour.com"));
        setField("distributedCrawl", false);
        lenient().when(ssrfGuard.evaluate(anyString()))
                .thenReturn(new SsrfGuard.SsrfVerdict(true, "ok"));
    }

    private void setField(String name, Object value) throws Exception {
        Field f = ScrapeEngine.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(scrapeEngine, value);
    }

    private InternalScrapeRequest requestWithoutProxy() {
        InternalScrapeRequest req = mock(InternalScrapeRequest.class);
        lenient().when(req.getJobId()).thenReturn(JOB_ID);
        lenient().when(req.getTargetUrl()).thenReturn("https://example.com");
        lenient().when(req.getJobType()).thenReturn(JobType.SCRAPER);
        lenient().when(req.getExtractionMode()).thenReturn(ExtractionMode.CSS_SELECTOR);
        lenient().when(req.getProxyConfig()).thenReturn(null);
        lenient().when(req.getRetryPolicy()).thenReturn(null);
        lenient().when(req.getProxy()).thenReturn(null);
        lenient().when(req.toBuilder()).thenReturn(
                InternalScrapeRequest.builder()
                        .jobId(JOB_ID).targetUrl("https://example.com")
                        .jobType(JobType.SCRAPER).extractionMode(ExtractionMode.CSS_SELECTOR));
        return req;
    }

    private InternalScrapeRequest requestWithProxy(ProxyConfig proxyConfig) {
        InternalScrapeRequest req = mock(InternalScrapeRequest.class);
        lenient().when(req.getJobId()).thenReturn(JOB_ID);
        lenient().when(req.getTargetUrl()).thenReturn("https://example.com");
        lenient().when(req.getJobType()).thenReturn(JobType.SCRAPER);
        lenient().when(req.getExtractionMode()).thenReturn(ExtractionMode.CSS_SELECTOR);
        lenient().when(req.getProxyConfig()).thenReturn(proxyConfig);
        lenient().when(req.getRetryPolicy()).thenReturn(null);
        lenient().when(req.getProxy()).thenReturn(null);
        lenient().when(req.toBuilder()).thenReturn(
                InternalScrapeRequest.builder()
                        .jobId(JOB_ID).targetUrl("https://example.com")
                        .jobType(JobType.SCRAPER).extractionMode(ExtractionMode.CSS_SELECTOR));
        return req;
    }

    private ProxyConfig enabledProxyConfig() {
        ProxyConfig cfg = mock(ProxyConfig.class);
        when(cfg.isEnabled()).thenReturn(true);
        return cfg;
    }

    private Proxy mockProxy() {
        Proxy proxy = mock(Proxy.class);
        when(proxy.id()).thenReturn(UUID.randomUUID());
        lenient().when(proxy.endpoint()).thenReturn("10.0.0.1:8080");
        return proxy;
    }

    private void makePipelineFail() {
        when(browserFactory.create(any()))
                .thenThrow(new RuntimeException("playwright_unavailable"));
    }

    @Nested
    @DisplayName("proxy routing")
    class ProxyRouting {

        @Test
        @DisplayName("no proxy config → pipeline failure propagates as Infrastructure5xxException")
        void noProxy_pipelineFailurePropagates() {
            InternalScrapeRequest req = requestWithoutProxy();
            makePipelineFail();
            assertThatThrownBy(() -> scrapeEngine.runScrape(req))
                    .isInstanceOf(Infrastructure5xxException.class);
        }

        @Test
        @DisplayName("disabled proxy config → same as no proxy, exception propagates")
        void disabledProxy_treatedAsNoProxy() {
            ProxyConfig cfg = mock(ProxyConfig.class);
            when(cfg.isEnabled()).thenReturn(false);
            InternalScrapeRequest req = requestWithProxy(cfg);
            makePipelineFail();
            assertThatThrownBy(() -> scrapeEngine.runScrape(req))
                    .isInstanceOf(Infrastructure5xxException.class);
            verify(proxyPoolService, never()).acquire(any(), any(), any());
        }

        @Test
        @DisplayName("proxy enabled → proxyPoolService.acquire() is called")
        void proxyEnabled_acquireIsCalled() {
            ProxyConfig cfg = enabledProxyConfig();
            InternalScrapeRequest req = requestWithProxy(cfg);
            when(proxyPoolService.acquire(eq(cfg), eq(JOB_ID), anyList()))
                    .thenReturn(Optional.empty());
            makePipelineFail();
            assertThatThrownBy(() -> scrapeEngine.runScrape(req))
                    .isInstanceOf(Infrastructure5xxException.class);
            verify(proxyPoolService).acquire(eq(cfg), eq(JOB_ID), anyList());
        }
    }

    @Nested
    @DisplayName("proxy acquisition")
    class ProxyAcquisition {

        @Test
        @DisplayName("acquire returns empty → falls back to direct IP")
        void acquireEmpty_fallsBackToDirect() {
            ProxyConfig cfg = enabledProxyConfig();
            InternalScrapeRequest req = requestWithProxy(cfg);
            when(proxyPoolService.acquire(any(), any(), anyList()))
                    .thenReturn(Optional.empty());
            makePipelineFail();
            assertThatThrownBy(() -> scrapeEngine.runScrape(req))
                    .isInstanceOf(Infrastructure5xxException.class);
            verify(proxyPoolService, times(1)).acquire(any(), any(), anyList());
        }

        @Test
        @DisplayName("acquire returns proxy → circuit breaker is reset before scraping")
        void acquireProxy_circuitBreakerReset() {
            ProxyConfig cfg = enabledProxyConfig();
            InternalScrapeRequest req = requestWithProxy(cfg);
            Proxy proxy = mockProxy();
            when(proxyPoolService.acquire(any(), any(), anyList()))
                    .thenReturn(Optional.of(proxy));
            makePipelineFail();
            assertThatCode(() -> scrapeEngine.runScrape(req))
                    .doesNotThrowAnyException();
            verify(cbRegistry).reset("https://example.com");
        }
    }

    @Nested
    @DisplayName("proxy release on failure")
    class ProxyRelease {

        @Test
        @DisplayName("proxy is released when pipeline throws (error isolation)")
        void proxyReleased_onPipelineException() {
            ProxyConfig cfg = enabledProxyConfig();
            InternalScrapeRequest req = requestWithProxy(cfg);
            Proxy proxy = mockProxy();
            when(proxyPoolService.acquire(any(), any(), anyList()))
                    .thenReturn(Optional.of(proxy));
            makePipelineFail();
            assertThatCode(() -> scrapeEngine.runScrape(req))
                    .doesNotThrowAnyException();
            verify(proxyPoolService).release(eq(proxy.id()), eq(JOB_ID), any(ProxyOutcome.class));
        }

        @Test
        @DisplayName("exception is SWALLOWED (not rethrown) when proxy is present")
        void withProxy_exceptionSwallowed() {
            ProxyConfig cfg = enabledProxyConfig();
            InternalScrapeRequest req = requestWithProxy(cfg);
            Proxy proxy = mockProxy();
            when(proxyPoolService.acquire(any(), any(), anyList()))
                    .thenReturn(Optional.of(proxy));
            makePipelineFail();
            assertThatCode(() -> scrapeEngine.runScrape(req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exception PROPAGATES when no proxy is present")
        void withoutProxy_exceptionPropagates() {
            InternalScrapeRequest req = requestWithoutProxy();
            makePipelineFail();
            assertThatThrownBy(() -> scrapeEngine.runScrape(req))
                    .isInstanceOf(Infrastructure5xxException.class);
        }
    }

    @Nested
    @DisplayName("proxy rotation")
    class ProxyRotation {

        @Test
        @DisplayName("rotation stops immediately when attempt outcome is NOT proxy-blocked")
        void rotation_stopsWhenNotBlocked() throws Exception {
            setField("maxProxyRotations", 3);
            ProxyConfig cfg = enabledProxyConfig();
            InternalScrapeRequest req = requestWithProxy(cfg);
            Proxy proxy = mockProxy();
            when(proxyPoolService.acquire(any(), any(), anyList()))
                    .thenReturn(Optional.of(proxy));
            makePipelineFail();
            scrapeEngine.runScrape(req);
            verify(proxyPoolService, times(1)).acquire(any(), any(), anyList());
        }

        @Test
        @DisplayName("maxProxyRotations=0 → loop body never runs, returns silently")
        void zeroRotations_loopSkippedNoFallback() throws Exception {
            setField("maxProxyRotations", 0);
            ProxyConfig cfg = enabledProxyConfig();
            InternalScrapeRequest req = requestWithProxy(cfg);
            assertThatCode(() -> scrapeEngine.runScrape(req))
                    .doesNotThrowAnyException();
            verify(proxyPoolService, never()).acquire(any(), any(), anyList());
        }
    }
}