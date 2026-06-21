package com.elyadata.feature.job.infrastructure.adapter;

import com.elyadata.feature.job.application.dto.SiteProbeResponse;
import com.elyadata.feature.job.application.dto.SiteProbeResponse.DetectedPagination;
import com.elyadata.feature.job.application.dto.SiteProbeResponse.DifficultyDto;
import com.elyadata.feature.job.application.dto.SiteProbeResponse.ProtectionDto;
import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.job.domain.model.SiteSignal;
import com.elyadata.feature.job.domain.service.SiteProbeService;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.crawler.pagination.PaginationDetector;
import com.elyadata.feature.scraper.crawler.robots.RobotsTxtGate;
import com.elyadata.feature.scraper.site.detector.SiteProtectionDetectorRegistry;
import com.elyadata.feature.scraper.site.dns.DnsResolver;
import com.elyadata.feature.scraper.site.dns.OriginIpResolver;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import com.elyadata.feature.scraper.site.model.DifficultyScore;
import com.elyadata.feature.scraper.site.model.OriginIpCandidate;
import com.elyadata.feature.scraper.site.model.SiteDnsProbe;
import com.elyadata.feature.scraper.site.scoring.ScrapingDifficultyScorer;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class SiteProbeHttpAdapter implements SiteProbeService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
    private static final int MIN_BODY_FOR_DOM_DETECTION = 1_000;
    private static final int MIN_BODY_FOR_SPA_DETECTION = 500;
    private static final long DNS_COLLECT_TIMEOUT_MS = 1_500;
    private static final Pattern LINK_NEXT = Pattern.compile(
            "<([^>]+)>\\s*;\\s*rel\\s*=\\s*\"?next\"?",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final PaginationDetector paginationDetector;
    private final OriginIpResolver originIpResolver;
    private final SiteProtectionDetectorRegistry protectionRegistry;
    private final ScrapingDifficultyScorer scorer;
    private final RobotsTxtGate robotsTxtGate;
    private final DnsResolver dnsResolver;

    public SiteProbeHttpAdapter(PaginationDetector paginationDetector, OriginIpResolver originIpResolver,
                                SiteProtectionDetectorRegistry protectionRegistry,
                                ScrapingDifficultyScorer scorer,
                                RobotsTxtGate robotsTxtGate,
                                DnsResolver dnsResolver) {
        this.paginationDetector = paginationDetector;
        this.originIpResolver = originIpResolver;
        this.protectionRegistry = protectionRegistry;
        this.scorer = scorer;
        this.robotsTxtGate = robotsTxtGate;
        this.dnsResolver = dnsResolver;
    }

    @Override
    public SiteProbeResponse probe(String url) {
        try {
            URI uri = URI.create(url);
            CompletableFuture<SiteDnsProbe> dnsFuture = CompletableFuture.supplyAsync(
                    () -> dnsResolver.resolve(uri.getHost()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            Map<String, List<String>> headers = response.headers().map();
            String body = response.body() != null ? response.body() : "";

            SiteDnsProbe dns = collectDns(dnsFuture, uri.getHost());

            List<DetectedProtection> protections = protectionRegistry.detectAll(status, headers, body, dns);

            List<OriginIpCandidate> originCandidates = List.of();
            boolean cdnDetected = protections.stream().anyMatch(p ->
                    p.provider() == AntiBotProvider.CLOUDFLARE
                            || p.provider() == AntiBotProvider.AKAMAI
                            || p.provider() == AntiBotProvider.DATADOME);

            if (cdnDetected) {
                originCandidates = originIpResolver.resolve(uri.getHost());
            }

            logProtectionDetails(url, protections);

            List<SiteSignal> signals = buildSignals(status, headers, body, protections);
            DetectedPagination pagination = detectPagination(url, response, signals);

            boolean robotsStrict = checkRobotsStrict(url);
            DifficultyScore difficulty = scorer.score(new ScrapingDifficultyScorer.ScoringInput(
                    protections,
                    signals,
                    pagination != null ? pagination.type() : null,
                    robotsStrict,
                    status
            ));

            log.info("[PROBE] url={} status={} signals={} protections={} difficulty={} ({}) "
                            + "dnsIps={} dnsNs={} paginationDetected={}",
                    url, status, signals, protections.size(),
                    difficulty.level(), difficulty.score(),
                    dns.resolvedIps().size(), dns.authoritativeNameservers().size(),
                    pagination != null ? pagination.type() : "skipped");

            return new SiteProbeResponse(
                    url,
                    signals,
                    status,
                    true,
                    pagination,
                    toProtectionDtos(protections),
                    toDifficultyDto(difficulty),
                    robotsStrict,
                    originCandidates.stream().map(SiteProbeResponse.OriginIpCandidateDto::from).toList()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[PROBE] Interrupted while probing url={}", url);
            return new SiteProbeResponse(url, List.of(), 0, false, null, List.of(), null, null);
        } catch (Exception e) {
            log.warn("[PROBE] Unreachable url={} reason={}", url, e.getMessage());
            return new SiteProbeResponse(url, List.of(), 0, false, null, List.of(), null, null);
        }
    }

    private SiteDnsProbe collectDns(CompletableFuture<SiteDnsProbe> dnsFuture, String host) {
        try {
            return dnsFuture.get(DNS_COLLECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("[PROBE] DNS lookup interrupted for {}: {}", host, e.getMessage());
            dnsFuture.cancel(true);
            return SiteDnsProbe.empty(host);
        } catch (Exception e) {
            log.debug("[PROBE] DNS lookup timed out/failed for {}: {}", host, e.getMessage());
            dnsFuture.cancel(true);
            return SiteDnsProbe.empty(host);
        }
    }

    private void logProtectionDetails(String url, List<DetectedProtection> protections) {
        if (protections.isEmpty()) {
            log.info("[PROBE] no protection detected for {}", url);
            return;
        }
        for (DetectedProtection p : protections) {
            String src;
            if (p.evidence().contains(" + ")) {
                src = "HTTP+DNS";
            } else if (p.evidence().contains("resolved IP") || p.evidence().contains("authoritative NS")) {
                src = "DNS";
            } else {
                src = "HTTP";
            }
            log.info("[PROBE] detected {} ({}) conf={} src={} — {}",
                    p.provider(), url, p.confidence(), src, p.evidence());
        }
    }


    private List<SiteSignal> buildSignals(int status,
                                          Map<String, List<String>> headers,
                                          String body,
                                          List<DetectedProtection> protections) {
        Set<SiteSignal> signals = EnumSet.noneOf(SiteSignal.class);

        for (DetectedProtection p : protections) {
            mapProtectionToSignal(p.provider()).ifPresent(signals::add);
        }

        addMiscSignals(status, headers, body, signals, protections);
        return new ArrayList<>(signals);
    }

    private Optional<SiteSignal> mapProtectionToSignal(AntiBotProvider provider) {
        return switch (provider) {
            case CLOUDFLARE -> Optional.of(SiteSignal.CLOUDFLARE);
            case DATADOME -> Optional.of(SiteSignal.DATADOME);
            case AKAMAI -> Optional.of(SiteSignal.AKAMAI);
            default -> Optional.empty();
        };
    }

    private void addMiscSignals(int status,
                                Map<String, List<String>> headers,
                                String body,
                                Set<SiteSignal> signals,
                                List<DetectedProtection> protections) {
        if (status == 401 || hasHeader(headers)) {
            signals.add(SiteSignal.AUTH_REQUIRED);
        }
        if (status == 429) {
            signals.add(SiteSignal.RATE_LIMITED);
        }

        boolean knownProvider = protections.stream().anyMatch(p ->
                p.provider() == AntiBotProvider.CLOUDFLARE
                        || p.provider() == AntiBotProvider.DATADOME
                        || p.provider() == AntiBotProvider.AKAMAI);
        if (status == 403 && !knownProvider) {
            signals.add(SiteSignal.LIKELY_BLOCKED);
        }

        String lowerBody = body.toLowerCase(Locale.ROOT);
        if (status == 200 && lowerBody.length() < 600 && signals.isEmpty()) {
            signals.add(SiteSignal.LIKELY_JS_RENDERED);
        }
    }

    private boolean hasHeader(Map<String, List<String>> headers) {
        return headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("www-authenticate"));
    }


    private boolean checkRobotsStrict(String url) {
        try {
            return !robotsTxtGate.isAllowed(url);
        } catch (Exception e) {
            log.debug("[PROBE] robots.txt check failed for {}: {}", url, e.getMessage());
            return false;
        }
    }


    private List<ProtectionDto> toProtectionDtos(List<DetectedProtection> protections) {
        return protections.stream()
                .map(p -> new ProtectionDto(p.provider(), p.confidence(), p.evidence()))
                .toList();
    }

    private DifficultyDto toDifficultyDto(DifficultyScore score) {
        return new DifficultyDto(
                score.level(),
                score.score(),
                score.recommendation(),
                score.factors().stream()
                        .map(DifficultyDto.FactorDto::from)
                        .toList()
        );
    }


    private DetectedPagination detectPagination(String url,
                                                HttpResponse<String> response,
                                                List<SiteSignal> signals) {
        int status = response.statusCode();

        if (isBlockedResponse(status, signals)) {
            log.info("[PROBE] anti-bot challenge response (status={}) — deferring pagination detection", status);
            return deferredDetection();
        }
        if (status < 200 || status >= 400) {
            return null;
        }

        DetectedPagination fromHeader = detectFromLinkHeader(response);
        if (fromHeader != null) {
            log.info("[PROBE] pagination detected via HTTP Link header: type={} param={}",
                    fromHeader.type(), fromHeader.suggestedSelector());
            return fromHeader;
        }

        String body = response.body();
        boolean spaShell = body != null && isSpaShell(body);
        int minBodyLength = spaShell ? MIN_BODY_FOR_SPA_DETECTION : MIN_BODY_FOR_DOM_DETECTION;

        if (body == null || body.length() < minBodyLength) {
            log.debug("[PROBE] body too small for pagination detection ({} chars, threshold={})",
                    body == null ? 0 : body.length(), minBodyLength);
            return null;
        }

        return detectFromBody(url, body, signals);
    }

    private boolean isBlockedResponse(int status, List<SiteSignal> signals) {
        boolean isChallengeStatus = (status == 403 || status == 503);
        boolean hasAntiBotSignal = signals.contains(SiteSignal.CLOUDFLARE)
                || signals.contains(SiteSignal.DATADOME)
                || signals.contains(SiteSignal.AKAMAI)
                || signals.contains(SiteSignal.LIKELY_BLOCKED);
        return isChallengeStatus && hasAntiBotSignal;
    }

    private DetectedPagination detectFromBody(String url, String body, List<SiteSignal> signals) {
        if (isHardChallengePage(body, signals)) {
            log.info("[PROBE] hard challenge page (Cloudflare/DataDome/Akamai) deferring pagination detection");
            return deferredDetection();
        }
        try {
            PageSnapshot snapshot = PageSnapshot.builder()
                    .html(body)
                    .url(url)
                    .build();

            if (signals.contains(SiteSignal.LIKELY_JS_RENDERED) || isSpaShell(body)) {
                log.info("[PROBE] SPA shell detected using SPA-aware pagination detection");
            }

            var report = paginationDetector.detect(snapshot);

            String reason = report.allCandidates().isEmpty()
                    ? "no signals found"
                    : report.allCandidates().getFirst().reason();

            List<DetectedPagination.Candidate> candidates = report.allCandidates().stream()
                    .map(c -> new DetectedPagination.Candidate(c.type(), c.confidence(), c.reason()))
                    .toList();

            return new DetectedPagination(
                    report.detectedType(),
                    report.confidence(),
                    report.confidenceLevel(),
                    reason,
                    report.suggestedSelector(),
                    candidates
            );
        } catch (Exception e) {
            log.warn("[PROBE] pagination detection failed: {}", e.getMessage());
            return null;
        }
    }

    private static DetectedPagination deferredDetection() {
        return new DetectedPagination(
                PaginationType.NONE,
                0.0,
                "deferred",
                "Site protected (Cloudflare/DataDome/Akamai) pagination will be detected automatically when the job runs, using a stealth browser that can bypass the protection.",
                null,
                List.of(),
                true
        );
    }

    private DetectedPagination detectFromLinkHeader(HttpResponse<String> response) {
        var linkHeaders = response.headers().allValues("link");
        for (String linkHeader : linkHeaders) {
            Matcher m = LINK_NEXT.matcher(linkHeader);
            if (m.find()) {
                String href = m.group(1);
                for (String p : new String[]{"page", "p", "pg", "offset"}) {
                    if (href.matches(".*[?&]" + p + "=\\d+.*")) {
                        return new DetectedPagination(
                                PaginationType.URL_PARAM, 0.98, "high",
                                "HTTP Link header rel=next uses ?" + p + "=N",
                                p, List.of());
                    }
                }
                return new DetectedPagination(
                        PaginationType.NEXT_BUTTON, 0.90, "high",
                        "HTTP Link header rel=next present: " + href,
                        null, List.of());
            }
        }
        return null;
    }

    private boolean isHardChallengePage(String body, List<SiteSignal> signals) {
        String lower = body.toLowerCase(Locale.ROOT);

        boolean cfChallenge = lower.contains("checking your browser")
                || lower.contains("just a moment")
                || lower.contains("cf-challenge")
                || lower.contains("__cf_chl_")
                || lower.contains("attention required");

        boolean ddChallenge = lower.contains("datadome-captcha")
                || lower.contains("blocked by datadome");

        boolean akamaiChallenge = lower.contains("access denied")
                && lower.contains("reference #");

        boolean structurallyBlocked = signals.contains(SiteSignal.LIKELY_BLOCKED);

        return cfChallenge || ddChallenge || akamaiChallenge || structurallyBlocked;
    }

    private boolean isSpaShell(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("__next_data__") || lower.contains("_next/static")
                || lower.contains("data-reactroot") || lower.contains("data-react-helmet")) {
            return true;
        }
        if (lower.contains("__nuxt__") || lower.contains("data-server-rendered")
                || lower.contains("data-v-app") || lower.contains("window.__nuxt")) {
            return true;
        }
        if (lower.contains("ng-version") || lower.contains("ng-app")
                || lower.contains("_nghost") || lower.contains("ngsw-config")) {
            return true;
        }
        return body.length() < 10_000 && lower.contains("<script") && lower.contains("bundle");
    }
}