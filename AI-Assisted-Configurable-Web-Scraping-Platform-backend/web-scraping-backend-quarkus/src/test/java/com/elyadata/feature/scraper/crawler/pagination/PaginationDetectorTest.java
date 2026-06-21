package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaginationDetector")
class PaginationDetectorTest {

    @Mock private PaginationStrategyRegistry registry;
    @Mock private SpaHydrationAnalyzer       hydrationAnalyzer;
    @Mock private LlmPaginationDetector      llmDetector;

    private PaginationDetector detector;

    @BeforeEach
    void setUp() {
        when(llmDetector.detect(any())).thenReturn(Optional.empty());
        detector = new PaginationDetector(registry, hydrationAnalyzer, llmDetector);
    }

    @Test
    @DisplayName("returns empty DetectionReport for null snapshot")
    void returnsEmptyForNull() {
        PaginationDetector.DetectionReport report = detector.detect(null);
        assertThat(report.detectedType()).isEqualTo(PaginationType.NONE);
        assertThat(report.confidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("returns empty DetectionReport when html is null")
    void returnsEmptyForNullHtml() {
        PageSnapshot snap = PageSnapshot.builder().url("https://ex.com").html(null).build();
        PaginationDetector.DetectionReport report = detector.detect(snap);
        assertThat(report.detectedType()).isEqualTo(PaginationType.NONE);
    }

    @Nested
    @DisplayName("link rel='next' fast path")
    class LinkRelNext {

        @Test
        @DisplayName("detects URL_PARAM via <link rel='next'> with page param")
        void detectsUrlParam() {
            String html = "<html><head><link rel='next' href='https://example.com?page=2'/></head><body></body></html>";
            PageSnapshot snap = PageSnapshot.builder().url("https://example.com").html(html).build();

            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.URL_PARAM);
            assertThat(report.confidence()).isGreaterThanOrEqualTo(0.90);
        }

        @Test
        @DisplayName("detects NEXT_BUTTON via <link rel='next'> with non-numeric href")
        void detectsNextButton() {
            String html = "<html><head><link rel='next' href='https://example.com/page/next'/></head><body></body></html>";
            PageSnapshot snap = PageSnapshot.builder().url("https://example.com").html(html).build();

            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.NEXT_BUTTON);
            assertThat(report.confidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("link rel='next' resolves before the LLM is ever consulted")
        void linkRelNextSkipsLlm() {
            String html = "<html><head><link rel='next' href='https://example.com?page=2'/></head><body></body></html>";
            PageSnapshot snap = PageSnapshot.builder().url("https://example.com").html(html).build();

            detector.detect(snap);
            verify(llmDetector, never()).detect(any());
        }
    }

    @Nested
    @DisplayName("Rendered page detection")
    class RenderedPageDetection {

        @Test
        @DisplayName("uses registered strategy detect() and picks best candidate")
        void usesBestCandidate() {
            UrlParamPagination urlStrategy = new UrlParamPagination();
            when(registry.getStrategy(PaginationType.URL_PARAM)).thenReturn(urlStrategy);

            String html = "<a href='?page=2'>2</a><a href='?page=3'>3</a>";
            PageSnapshot snap = PageSnapshot.builder().url("https://example.com").html(html).build();

            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.URL_PARAM);
            assertThat(report.confidence()).isGreaterThanOrEqualTo(0.70);
        }

        @Test
        @DisplayName("a strong deterministic candidate does NOT trigger the LLM")
        void strongCandidateSkipsLlm() {
            UrlParamPagination urlStrategy = new UrlParamPagination();
            when(registry.getStrategy(PaginationType.URL_PARAM)).thenReturn(urlStrategy);

            String html = "<a href='?page=2'>2</a><a href='?page=3'>3</a>";
            PageSnapshot snap = PageSnapshot.builder().url("https://example.com").html(html).build();

            detector.detect(snap);
            verify(llmDetector, never()).detect(any());
        }

        @Test
        @DisplayName("returns NONE when no strategy detects pagination")
        void returnsNoneWhenNoMatch() {
            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com")
                    .html("<div>static page</div>")
                    .build();
            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.NONE);
        }
    }

    @Nested
    @DisplayName("SPA shell detection")
    class SpaDetection {

        @Test
        @DisplayName("delegates to hydrationAnalyzer when SPA shell detected")
        void delegatesToHydrationAnalyzer() {
            String spaHtml = """
                    <html><head>
                    <script src="/_next/static/chunk.js"></script>
                    </head><body><div id="__next"></div></body></html>
                    """;

            SpaHydrationAnalyzer.Finding finding = new SpaHydrationAnalyzer.Finding(
                    PaginationType.NUMBERED, 0.85, "totalPages found", null, "next.js");
            when(hydrationAnalyzer.analyze(anyString())).thenReturn(Optional.of(finding));

            PageSnapshot snap = PageSnapshot.builder().url("https://example.com").html(spaHtml).build();
            PaginationDetector.DetectionReport report = detector.detect(snap);

            assertThat(report.detectedType()).isEqualTo(PaginationType.NUMBERED);
            assertThat(report.confidence()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("uses URL param fallback when SPA seed URL has page param")
        void usesSpaUrlParamFallback() {
            when(hydrationAnalyzer.analyze(anyString())).thenReturn(Optional.empty());
            String spaHtml = "<html><body><script src='/bundle.js'></script></body></html>";
            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com/items?page=1")
                    .html(spaHtml)
                    .build();

            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.URL_PARAM);
        }

        @Test
        @DisplayName("falls back to INFINITE_SCROLL for large SPA with no signals")
        void spaFallbackInfiniteScroll() {
            when(hydrationAnalyzer.analyze(anyString())).thenReturn(Optional.empty());
            String spaHtml = "<html><body><script src='/bundle.js'></script>" +
                    "x".repeat(6_000) + "</body></html>";
            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com/")
                    .html(spaHtml)
                    .build();

            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.INFINITE_SCROLL);
            assertThat(report.confidence()).isLessThan(0.50); // low confidence fallback
        }
    }

    @Nested
    @DisplayName("LLM fallback")
    class LlmFallback {

        @Test
        @DisplayName("LLM is consulted and its result is used when no deterministic candidate exists")
        void llmUsedWhenNoCandidate() {
            when(llmDetector.detect(any())).thenReturn(Optional.of(
                    new PaginationStrategy.DetectionResult(
                            PaginationType.LOAD_MORE, 0.60, "LLM fallback: load-more button", ".load")));

            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com")
                    .html("<div>nothing the deterministic detectors recognise</div>")
                    .build();

            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.LOAD_MORE);
            assertThat(report.confidence()).isEqualTo(0.60);
            verify(llmDetector).detect(any());
        }

        @Test
        @DisplayName("LLM result is preferred over a non-SPA empty result")
        void llmBeatsEmpty() {
            when(llmDetector.detect(any())).thenReturn(Optional.of(
                    new PaginationStrategy.DetectionResult(
                            PaginationType.NEXT_BUTTON, 0.55, "LLM fallback", null)));

            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com")
                    .html("<div>static page, no signals</div>")
                    .build();

            PaginationDetector.DetectionReport report = detector.detect(snap);
            assertThat(report.detectedType()).isEqualTo(PaginationType.NEXT_BUTTON);
        }
    }

    @Nested
    @DisplayName("DetectionReport.confidenceLevel()")
    class ConfidenceLevel {

        @Test
        @DisplayName("returns 'high' for confidence >= 0.85")
        void high() {
            var report = new PaginationDetector.DetectionReport(
                    PaginationType.URL_PARAM, 0.95, java.util.List.of(), null);
            assertThat(report.confidenceLevel()).isEqualTo("high");
        }

        @Test
        @DisplayName("returns 'medium' for confidence in [0.65, 0.85)")
        void medium() {
            var report = new PaginationDetector.DetectionReport(
                    PaginationType.NUMBERED, 0.70, java.util.List.of(), null);
            assertThat(report.confidenceLevel()).isEqualTo("medium");
        }

        @Test
        @DisplayName("returns 'low' for confidence in [0.30, 0.65)")
        void low() {
            var report = new PaginationDetector.DetectionReport(
                    PaginationType.INFINITE_SCROLL, 0.30, java.util.List.of(), null);
            assertThat(report.confidenceLevel()).isEqualTo("low");
        }

        @Test
        @DisplayName("returns 'none' for confidence below 0.30")
        void noneLevel() {
            var report = new PaginationDetector.DetectionReport(
                    PaginationType.NONE, 0.0, java.util.List.of(), null);
            assertThat(report.confidenceLevel()).isEqualTo("none");
        }
    }
}