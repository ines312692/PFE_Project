package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class PaginationDetector {
    private static final double MIN_CONFIDENCE_RENDERED = 0.50;
    private static final double MIN_CONFIDENCE_SPA = 0.25;

    private static final List<PaginationType> PRIORITY_ORDER = List.of(
            PaginationType.URL_PARAM,
            PaginationType.NUMBERED,
            PaginationType.NEXT_BUTTON,
            PaginationType.LOAD_MORE,
            PaginationType.INFINITE_SCROLL
    );

    private final PaginationStrategyRegistry registry;
    private final SpaHydrationAnalyzer hydrationAnalyzer;
    private final LlmPaginationDetector llmDetector;

    @Inject
    public PaginationDetector(PaginationStrategyRegistry registry,
                              SpaHydrationAnalyzer hydrationAnalyzer,
                              LlmPaginationDetector llmDetector) {
        this.registry = registry;
        this.hydrationAnalyzer = hydrationAnalyzer;
        this.llmDetector = llmDetector;
    }

    public DetectionReport detect(PageSnapshot snapshot) {
        if (snapshot == null || snapshot.getHtml() == null) {
            log.warn("[PaginationDetector] empty snapshot — cannot detect");
            return DetectionReport.empty();
        }

        boolean isSpa = detectSpaShell(snapshot.getHtml());
        double minConfidence = isSpa ? MIN_CONFIDENCE_SPA : MIN_CONFIDENCE_RENDERED;
        DetectionReport relNextResult = detectFromLinkRelNext(snapshot);
        if (relNextResult != null) return relNextResult;

        if (isSpa) {
            log.info("[PaginationDetector] SPA shell detected — applying SPA-aware heuristics");
            DetectionReport spaResult = detectForSpa(snapshot);
            if (spaResult != null) return spaResult;
        }
        List<PaginationStrategy.DetectionResult> candidates = PRIORITY_ORDER.stream()
                .map(registry::getStrategy)
                .filter(Objects::nonNull)
                .map(s -> {
                    try {
                        return s.detect(snapshot);
                    } catch (Exception e) {
                        log.warn("[PaginationDetector] {} detect() failed: {}",
                                s.type(), e.getMessage());
                        return PaginationStrategy.DetectionResult.notFound(s.type());
                    }
                })
                .filter(PaginationStrategy.DetectionResult::isFound)
                .sorted(Comparator
                        .comparingDouble(PaginationStrategy.DetectionResult::confidence)
                        .reversed()
                        .thenComparingInt(r -> PRIORITY_ORDER.indexOf(r.type())))
                .toList();

        if (candidates.isEmpty()) {
            // No deterministic signal at all — ask the LLM before giving up / SPA-guessing.
            DetectionReport llm = tryLlm(snapshot, "no deterministic candidates");
            if (llm != null) return llm;

            if (isSpa) {
                return spaFallback(snapshot);
            }
            log.info("[PaginationDetector] no pagination signals detected");
            return DetectionReport.empty();
        }

        var best = candidates.getFirst();
        if (best.confidence() < minConfidence) {
            log.info("[PaginationDetector] best candidate {} below threshold ({} < {} isSpa={})",
                    best.type(), best.confidence(), minConfidence, isSpa);

            // Deterministic signal exists but is too weak — let the LLM try to beat it.
            DetectionReport llm = tryLlm(snapshot, "best candidate below threshold");
            if (llm != null && llm.confidence() >= best.confidence()) {
                return llm;
            }
            return new DetectionReport(PaginationType.NONE, best.confidence(), candidates, null);
        }

        log.info("[PaginationDetector] detected {} (confidence={}) — {} candidates total (isSpa={})",
                best.type(), best.confidence(), candidates.size(), isSpa);

        return new DetectionReport(
                best.type(),
                best.confidence(),
                candidates,
                best.suggestedSelector()
        );
    }

    private DetectionReport tryLlm(PageSnapshot snapshot, String trigger) {
        log.info("[PaginationDetector] invoking LLM fallback ({})", trigger);
        Optional<PaginationStrategy.DetectionResult> llm = llmDetector.detect(snapshot);
        if (llm.isEmpty()) {
            log.info("[PaginationDetector] LLM fallback produced nothing usable");
            return null;
        }
        var r = llm.get();
        log.info("[PaginationDetector] LLM fallback → {} (confidence={})", r.type(), r.confidence());
        return new DetectionReport(r.type(), r.confidence(), List.of(r), r.suggestedSelector());
    }

    private DetectionReport detectFromLinkRelNext(PageSnapshot snapshot) {
        String html = snapshot.getHtml();
        String url = snapshot.getUrl() != null ? snapshot.getUrl() : "";
        Document doc = Jsoup.parse(html, url);
        Element relNext = doc.selectFirst("link[rel=next], link[rel='next']");
        if (relNext == null || relNext.attr("href").isBlank()) return null;

        String href = relNext.absUrl("href");
        if (href.isBlank()) href = relNext.attr("href");

        log.info("[PaginationDetector] <link rel='next'> → {}", href);

        for (String param : new String[]{"page", "p", "pg", "offset", "start", "from"}) {
            if (href.matches(".*[?&]" + param + "=\\d+.*")) {
                return new DetectionReport(
                        PaginationType.URL_PARAM, 0.97,
                        List.of(candidate(PaginationType.URL_PARAM, 0.97,
                                "<link rel='next'> uses ?" + param + "=N")),
                        param);
            }
        }

        return new DetectionReport(
                PaginationType.NEXT_BUTTON, 0.90,
                List.of(candidate(PaginationType.NEXT_BUTTON, 0.90,
                        "<link rel='next'> present (non-numeric href)")),
                null);
    }

    private DetectionReport detectForSpa(PageSnapshot snapshot) {
        String html = snapshot.getHtml();
        String lower = html.toLowerCase(Locale.ROOT);
        String url = snapshot.getUrl() != null ? snapshot.getUrl() : "";

        var hydration = hydrationAnalyzer.analyze(html);
        if (hydration.isPresent()) {
            var f = hydration.get();
            log.info("[PaginationDetector/SPA] hydration({}) → {} conf={} reason={}",
                    f.framework(), f.type(), f.confidence(), f.reason());
            return new DetectionReport(
                    f.type(), f.confidence(),
                    List.of(candidate(f.type(), f.confidence(), f.reason())),
                    f.suggestedSelector());
        }

        DetectionReport urlParamResult = detectSpaUrlParam(url);
        if (urlParamResult != null) return urlParamResult;

        DetectionReport infiniteResult = detectSpaInfiniteScroll(lower);
        if (infiniteResult != null) return infiniteResult;

        return detectSpaKeywordPagination(lower);
    }

    private DetectionReport detectSpaUrlParam(String url) {
        for (String param : new String[]{"page", "p", "pg", "offset", "start", "from"}) {
            if (url.matches(".*[?&]" + param + "=\\d+.*")) {
                log.info("[PaginationDetector/SPA] URL param '{}' detected in seed URL", param);
                return new DetectionReport(
                        PaginationType.URL_PARAM, 0.90,
                        List.of(candidate(PaginationType.URL_PARAM, 0.90,
                                "SPA: URL already uses ?" + param + "=N")),
                        param);
            }
        }
        return null;
    }

    private DetectionReport detectSpaInfiniteScroll(String lower) {
        int score = 0;
        StringBuilder reasons = new StringBuilder();

        if (lower.contains("intersectionobserver")) {
            score += 25;
            reasons.append("IntersectionObserver; ");
        }
        if (lower.contains("infinite-scroll") || lower.contains("infinitescroll")
                || lower.contains("react-infinite-scroller")
                || lower.contains("ngx-infinite-scroll")
                || lower.contains("vue-infinite-scroll")) {
            score += 40;
            reasons.append("infinite-scroll lib; ");
        }
        if (lower.contains("cdk-virtual-scroll") || lower.contains("virtual-scroller")
                || lower.contains("virtualscroll") || lower.contains("virtual-list")) {
            score += 35;
            reasons.append("virtual scroll component; ");
        }
        if (lower.contains("\"currentpage\"") || lower.contains("\"current_page\"")
                || lower.contains("\"pagenumber\"") || lower.contains("\"totalpage")) {
            score += 30;
            reasons.append("pagination keys in embedded state; ");
        }

        if (score < 40) return null;

        double conf = Math.min(0.25 + score / 150.0, 0.75);
        log.info("[PaginationDetector/SPA] infinite scroll heuristic score={} conf={}", score, conf);
        return new DetectionReport(
                PaginationType.INFINITE_SCROLL, conf,
                List.of(candidate(PaginationType.INFINITE_SCROLL, conf,
                        "SPA shell: " + reasons)),
                null);
    }

    private DetectionReport detectSpaKeywordPagination(String lower) {
        if (lower.contains("\"totalpages\"") || lower.contains("\"total_pages\"")
                || lower.contains("\"pagecount\"") || lower.contains("\"page_count\"")
                || lower.contains("\"numpages\"")) {
            log.info("[PaginationDetector/SPA] numbered pagination keys in embedded state");
            return new DetectionReport(
                    PaginationType.NUMBERED, 0.60,
                    List.of(candidate(PaginationType.NUMBERED, 0.60,
                            "SPA shell: totalPages/pageCount found in embedded JSON")),
                    null);
        }

        if (lower.contains("\"endcursor\"") || lower.contains("\"hasnextpage\"")
                || lower.contains("\"pageinfo\"") || lower.contains("\"cursorbased\"")) {
            log.info("[PaginationDetector/SPA] GraphQL cursor pagination detected in embedded state");
            return new DetectionReport(
                    PaginationType.CURSOR, 0.70,
                    List.of(candidate(PaginationType.CURSOR, 0.70,
                            "SPA shell: GraphQL Relay cursor (endCursor/hasNextPage)")),
                    null);
        }

        return null;
    }

    private DetectionReport spaFallback(PageSnapshot snapshot) {
        String html = snapshot.getHtml() != null ? snapshot.getHtml() : "";
        if (html.length() > 5_000) {
            log.info("[PaginationDetector/SPA] no clear signals, falling back to INFINITE_SCROLL (low confidence)");
            return new DetectionReport(
                    PaginationType.INFINITE_SCROLL, 0.30,
                    List.of(candidate(PaginationType.INFINITE_SCROLL, 0.30,
                            "SPA fallback: no DOM signals, INFINITE_SCROLL assumed")),
                    null);
        }
        log.info("[PaginationDetector/SPA] shell too small for fallback — returning NONE");
        return DetectionReport.empty();
    }

    private static boolean detectSpaShell(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("__next_data__") || lower.contains("_next/static")) return true;
        if (lower.contains("__nuxt__") || lower.contains("data-server-rendered")
                || lower.contains("window.__nuxt") || lower.contains("data-v-app")) return true;
        if (lower.contains("ng-version") || lower.contains("_nghost")
                || lower.contains("ngsw-config")) return true;
        if (lower.contains("data-reactroot") || lower.contains("data-react-helmet")) return true;
        return html.length() < 15_000
                && lower.contains("<script")
                && (lower.contains("bundle") || lower.contains("chunk") || lower.contains("main."));
    }

    private static PaginationStrategy.DetectionResult candidate(PaginationType type,
                                                                double confidence,
                                                                String reason) {
        return new PaginationStrategy.DetectionResult(type, confidence, reason, null);
    }

    public record DetectionReport(
            PaginationType detectedType,
            double confidence,
            List<PaginationStrategy.DetectionResult> allCandidates,
            String suggestedSelector
    ) {
        public static DetectionReport empty() {
            return new DetectionReport(PaginationType.NONE, 0.0, List.of(), null);
        }

        public String confidenceLevel() {
            if (confidence >= 0.85) return "high";
            if (confidence >= 0.65) return "medium";
            if (confidence >= 0.30) return "low";
            return "none";
        }
    }
}