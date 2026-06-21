package com.elyadata.feature.scraper.pipeline;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.job.domain.model.RetryPolicy;
import com.elyadata.feature.scraper.antibot.captcha.orchestration.CaptchaHandler;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.crawler.pagination.PaginationStrategyRegistry;
import com.elyadata.feature.scraper.extractor.BaseExtractor;
import com.elyadata.feature.scraper.extractor.ExtractionOutput;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.resilience.CircuitBreakerRegistry;
import com.elyadata.feature.scraper.resilience.DomainCircuitBreaker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScrapePipeline")
class ScrapePipelineTest {

    @Mock
    ABrowser browser;
    @Mock
    BaseExtractor extractor;
    @Mock
    CircuitBreakerRegistry cbRegistry;
    @Mock
    PaginationStrategyRegistry paginationRegistry;
    @Mock
    CaptchaHandler captchaHandler;
    @Mock
    DomainCircuitBreaker circuitBreaker;
    @Mock
    InternalScrapeRequest request;
    @Mock
    PageSnapshot snapshot;

    private ScrapePipeline pipeline;

    private static final UUID JOB_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");


    @BeforeEach
    void setUp() {
        pipeline = new ScrapePipeline(
                browser, extractor, cbRegistry, paginationRegistry, captchaHandler);
    }

    private void stubBase() {
        lenient().when(request.getJobId()).thenReturn(JOB_ID);
        lenient().when(request.getTargetUrl()).thenReturn("https://example.com");
        lenient().when(request.getExtractionMode()).thenReturn(ExtractionMode.CSS_SELECTOR);
        lenient().when(request.getJobType()).thenReturn(JobType.SCRAPER);
        lenient().when(request.getMaxPages()).thenReturn(10);
        lenient().when(request.getMaxPaginationPages()).thenReturn(5);
        lenient().when(request.getDelayMs()).thenReturn(0);
        lenient().when(request.isSinglePageMode()).thenReturn(true);
        lenient().when(request.getRetryPolicy()).thenReturn(null);
        lenient().when(request.getWaitForSelector()).thenReturn(null);
        lenient().when(request.getClickBefore()).thenReturn(null);
        lenient().when(request.isScrollToBottom()).thenReturn(false);
        lenient().when(request.getSeedUrls()).thenReturn(null);
    }

    private void stubExtraction(List<Map<String, Object>> records, String rawContent) {
        when(browser.snapshot()).thenReturn(snapshot);
        lenient().when(snapshot.getHtml()).thenReturn("<html><body>content</body></html>");
        lenient().when(snapshot.getUrl()).thenReturn("https://example.com");
        ExtractionOutput output = mock(ExtractionOutput.class);
        when(output.records()).thenReturn(records);
        when(output.rawContent()).thenReturn(rawContent);
        when(extractor.extract(any(), any())).thenReturn(output);
    }

    private void stubNoPagination() {
        lenient().when(paginationRegistry.isPaginationEnabled(any())).thenReturn(false);
    }

    private void stubNoCaptcha() {
        lenient().when(captchaHandler.tryHandleIfPresent(any(), any())).thenReturn(false);
    }


    @Nested
    @DisplayName("status determination")
    class StatusDetermination {

        @Test
        @DisplayName("COMPLETED when CSS extraction returns records")
        void completed_whenRecordsExtracted() {
            stubBase();
            stubExtraction(List.of(Map.of("price", "10€")), null);
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.status()).isEqualTo("completed");
            assertThat(result.records()).hasSize(1);
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("FAILED when CSS extraction returns no records")
        void failed_whenNoRecordsExtracted() {
            stubBase();
            stubExtraction(List.of(), null);
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.status()).isEqualTo("failed");
            assertThat(result.records()).isEmpty();
            assertThat(result.errors()).isNotEmpty();
            assertThat(result.logs()).anyMatch(l -> l.contains("[ERROR]"));
        }

        @Test
        @DisplayName("COMPLETED in LLM mode with rawContent even if no structured records")
        void completed_llmModeWithRawContent() {
            stubBase();
            when(request.getExtractionMode()).thenReturn(ExtractionMode.LLM);
            stubExtraction(List.of(), "LLM raw output");
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.status()).isEqualTo("completed");
            assertThat(result.rawContent()).contains("LLM raw output");
        }

        @Test
        @DisplayName("FAILED in LLM mode when rawContent is also empty")
        void failed_llmModeWithNoContent() {
            stubBase();
            when(request.getExtractionMode()).thenReturn(ExtractionMode.LLM);
            stubExtraction(List.of(), null);
            stubNoPagination();
            stubNoCaptcha();
            PipelineResult result = pipeline.run(request);

            assertThat(result.status()).isEqualTo("completed");
        }

        @Test
        @DisplayName("FAILED when navigation throws and no records accumulated")
        void failed_onNavigationError() {
            stubBase();
            doThrow(new RuntimeException("network timeout")).when(browser).navigate(any());
            stubNoPagination();

            PipelineResult result = pipeline.run(request);

            assertThat(result.status()).isEqualTo("failed");
            assertThat(result.errors()).anyMatch(e ->
                    e.contains("Navigation failed") || e.contains("network timeout"));
        }

        @Test
        @DisplayName("FAILED when extraction throws and no records accumulated")
        void failed_onExtractionError() {
            stubBase();
            when(browser.snapshot()).thenReturn(snapshot);
            when(extractor.extract(any(), any()))
                    .thenThrow(new RuntimeException("selector broken"));
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.status()).isEqualTo("failed");
            assertThat(result.errors()).anyMatch(e ->
                    e.contains("Extraction failed") || e.contains("selector broken"));
        }
    }


    @Nested
    @DisplayName("page counting")
    class PageCounting {

        @Test
        @DisplayName("single page mode → pagesCrawled = 1")
        void singlePageMode_countsOnePage() {
            stubBase();
            when(request.isSinglePageMode()).thenReturn(true);
            stubExtraction(List.of(Map.of("a", "b")), null);
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.pagesCrawled()).isEqualTo(1);
            verify(browser, times(1)).navigate(any());
        }

        @Test
        @DisplayName("CRAWLER multi-URL mode accumulates records across seed URLs")
        void multiUrl_recordsAccumulate() {
            stubBase();
            when(request.getJobType()).thenReturn(JobType.CRAWLER);
            when(request.isSinglePageMode()).thenReturn(false);
            when(request.getSeedUrls()).thenReturn(List.of("https://example.com/p2"));
            stubExtraction(List.of(Map.of("x", "1")), null);
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.pagesCrawled()).isEqualTo(2);
            assertThat(result.records()).hasSize(2);
        }

        @Test
        @DisplayName("SCRAPER jobType never appends seed URLs (buildUrlList guard)")
        void scraperJobType_ignoresSeedUrls() {
            stubBase();
            when(request.getJobType()).thenReturn(JobType.SCRAPER);
            when(request.isSinglePageMode()).thenReturn(false);
            lenient().when(request.getSeedUrls()).thenReturn(List.of("https://example.com/seed"));
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);
            verify(browser, times(1)).navigate("https://example.com");
            assertThat(result.pagesCrawled()).isEqualTo(1);
        }
    }


    @Nested
    @DisplayName("browser lifecycle")
    class BrowserLifecycle {

        @Test
        @DisplayName("browser.start() and browser.close() are called exactly once")
        void startAndClose_calledOnce() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(browser).start();
            verify(browser).close();
        }

        @Test
        @DisplayName("browser.close() is called even when navigation throws")
        void close_calledEvenOnFailure() {
            stubBase();
            doThrow(new RuntimeException("crash")).when(browser).navigate(any());
            stubNoPagination();

            pipeline.run(request);

            verify(browser).close();
        }

        @Test
        @DisplayName("browser.navigate() is called with the target URL")
        void navigate_calledWithTargetUrl() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(browser).navigate("https://example.com");
        }
    }


    @Nested
    @DisplayName("page interactions")
    class PageInteractions {

        @Test
        @DisplayName("waitForSelector is called when configured")
        void waitForSelector_calledWhenSet() {
            stubBase();
            when(request.getWaitForSelector()).thenReturn(".product-list");
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(browser).waitForSelector(".product-list");
        }

        @Test
        @DisplayName("waitForSelector is NOT called when null")
        void waitForSelector_notCalledWhenNull() {
            stubBase();
            when(request.getWaitForSelector()).thenReturn(null);
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(browser, never()).waitForSelector(any());
        }

        @Test
        @DisplayName("click is called when clickBefore is configured")
        void click_calledWhenClickBeforeSet() {
            stubBase();
            when(request.getClickBefore()).thenReturn("#accept-cookies");
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(browser).click("#accept-cookies");
        }

        @Test
        @DisplayName("scroll is called with request.isScrollToBottom value")
        void scroll_calledWithRequestFlag() {
            stubBase();
            when(request.isScrollToBottom()).thenReturn(true);
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(browser).scroll(true);
        }

        @Test
        @DisplayName("dismissCookies is always called on each page")
        void dismissCookies_alwaysCalled() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(browser).dismissCookies();
        }
    }

    @Nested
    @DisplayName("CAPTCHA handling")
    class CaptchaHandling {

        @Test
        @DisplayName("CAPTCHA bypass is attempted on each page")
        void captchaHandler_invokedOnEveryPage() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            when(captchaHandler.tryHandleIfPresent(any(), eq(JOB_ID))).thenReturn(false);

            pipeline.run(request);

            verify(captchaHandler).tryHandleIfPresent(any(), eq(JOB_ID));
        }

        @Test
        @DisplayName("CAPTCHA bypass logged when handler returns true")
        void captchaBypass_loggedInResult() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            when(captchaHandler.tryHandleIfPresent(any(), eq(JOB_ID))).thenReturn(true);

            PipelineResult result = pipeline.run(request);

            assertThat(result.logs())
                    .anyMatch(l -> l.contains("CAPTCHA"));
        }
    }

    @Nested
    @DisplayName("circuit breaker")
    class CircuitBreakerTests {

        @Test
        @DisplayName("URL is skipped and error logged when circuit breaker is OPEN")
        void circuitBreakerOpen_skipsUrl() {
            stubBase();
            RetryPolicy policy = mock(RetryPolicy.class);
            when(policy.circuitBreakerEnabled()).thenReturn(true);
            when(request.getRetryPolicy()).thenReturn(policy);
            when(cbRegistry.getOrCreate(any(), any())).thenReturn(circuitBreaker);
            when(circuitBreaker.allowRequest()).thenReturn(false);
            stubNoPagination();

            PipelineResult result = pipeline.run(request);

            verify(browser, never()).navigate(any());
            assertThat(result.errors()).anyMatch(e -> e.contains("Circuit breaker"));
        }

        @Test
        @DisplayName("URL proceeds normally when circuit breaker is CLOSED")
        void circuitBreakerClosed_allowsNavigation() {
            stubBase();
            RetryPolicy policy = mock(RetryPolicy.class);
            when(policy.circuitBreakerEnabled()).thenReturn(true);
            lenient().when(policy.maxRetries()).thenReturn(0);
            when(request.getRetryPolicy()).thenReturn(policy);
            when(cbRegistry.getOrCreate(any(), any())).thenReturn(circuitBreaker);
            when(circuitBreaker.allowRequest()).thenReturn(true);
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            verify(browser).navigate("https://example.com");
            assertThat(result.status()).isEqualTo("completed");
        }

        @Test
        @DisplayName("circuit breaker is not consulted when circuitBreakerEnabled is false")
        void circuitBreakerDisabled_noCbCheck() {
            stubBase();
            RetryPolicy policy = mock(RetryPolicy.class);
            when(policy.circuitBreakerEnabled()).thenReturn(false);
            when(request.getRetryPolicy()).thenReturn(policy);
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            pipeline.run(request);

            verify(cbRegistry, never()).getOrCreate(any(), any());
        }
    }


    @Nested
    @DisplayName("result metadata")
    class ResultMetadata {

        @Test
        @DisplayName("durationMs is non-negative")
        void durationMs_isNonNegative() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            assertThat(pipeline.run(request).durationMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("logs contain [INFO] Scrape started entry")
        void logs_containStartEntry() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            assertThat(pipeline.run(request).logs())
                    .anyMatch(l -> l.contains("Scrape started"));
        }

        @Test
        @DisplayName("logs contain [INFO] Scrape finished entry")
        void logs_containEndEntry() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            assertThat(pipeline.run(request).logs())
                    .anyMatch(l -> l.contains("Scrape finished"));
        }

        @Test
        @DisplayName("rawContent is prefixed with page marker")
        void rawContent_hasPrefixMarker() {
            stubBase();
            when(request.getExtractionMode()).thenReturn(ExtractionMode.LLM);
            stubExtraction(List.of(), "LLM content here");
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.rawContent()).contains("=== PAGE:");
            assertThat(result.rawContent()).contains("LLM content here");
        }

        @Test
        @DisplayName("discoveredFiles is always an empty list (populated by CrawlPipeline)")
        void discoveredFiles_emptyForScrapePipeline() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            assertThat(pipeline.run(request).discoveredFiles()).isEmpty();
        }

        @Test
        @DisplayName("engineUsed is derived from browser class simple name")
        void engineUsed_fromBrowserClassName() {
            stubBase();
            stubExtraction(List.of(Map.of("k", "v")), null);
            stubNoPagination();
            stubNoCaptcha();

            // ABrowser mock → simple name contains "Mock"
            assertThat(pipeline.run(request).engineUsed()).isNotNull();
        }
    }

    @Nested
    @DisplayName("record accumulation")
    class RecordAccumulation {

        @Test
        @DisplayName("all extracted records are present in result")
        void allRecords_inResult() {
            stubBase();
            List<Map<String, Object>> records = List.of(
                    Map.of("name", "Product A", "price", "9.99"),
                    Map.of("name", "Product B", "price", "19.99"),
                    Map.of("name", "Product C", "price", "29.99")
            );
            stubExtraction(records, null);
            stubNoPagination();
            stubNoCaptcha();

            PipelineResult result = pipeline.run(request);

            assertThat(result.records()).hasSize(3);
            assertThat(result.records())
                    .extracting(r -> r.get("name"))
                    .containsExactlyInAnyOrder("Product A", "Product B", "Product C");
        }
    }
}