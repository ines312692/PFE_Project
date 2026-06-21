package com.elyadata.feature.scraper.site.dns;

import com.elyadata.feature.scraper.site.dns.history.DnsHistoryProviderRegistry;
import com.elyadata.feature.scraper.site.model.HistoricalDnsRecord;
import com.elyadata.feature.scraper.site.model.OriginIpCandidate;
import com.elyadata.feature.scraper.site.model.OriginIpCandidate.ValidationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class OriginIpResolver {

    private final DnsHistoryProviderRegistry historyRegistry;
    private final CdnIpRangeRegistry cdnRegistry;
    private final OriginIpValidator validator;

    @ConfigProperty(name = "dns-history.resolver.max-candidates", defaultValue = "10")
    int maxCandidates;

    @ConfigProperty(name = "dns-history.resolver.validation-parallelism", defaultValue = "5")
    int validationParallelism;

    @ConfigProperty(name = "dns-history.resolver.validate", defaultValue = "true")
    boolean validateEnabled;

    public List<OriginIpCandidate> resolve(String host) {
        long start = System.currentTimeMillis();

        List<HistoricalDnsRecord> history = historyRegistry.aggregateHistory(host);
        if (history.isEmpty()) {
            log.info("[ORIGIN] no historical records for {}", host);
            return List.of();
        }

        List<HistoricalDnsRecord> nonCdn = history.stream()
                .filter(r -> !cdnRegistry.isCdnIp(r.ip()))
                .limit(maxCandidates)
                .toList();

        log.info("[ORIGIN] {} historical IPs for {}, {} kept after CDN filter",
                history.size(), host, nonCdn.size());

        if (nonCdn.isEmpty()) return List.of();

        List<OriginIpCandidate> candidates = validateEnabled
                ? validateAll(host, nonCdn)
                : nonCdn.stream().map(this::toUnvalidated).toList();

        List<OriginIpCandidate> ranked = candidates.stream()
                .sorted(Comparator
                        .comparing(OriginIpCandidate::validationStatus,
                                Comparator.comparing(this::statusRank))
                        .thenComparing(OriginIpCandidate::confidence, Comparator.reverseOrder()))
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        long reachable = ranked.stream().filter(OriginIpCandidate::isUsable).count();
        log.info("[ORIGIN] {} → {} candidate(s), {} reachable, in {}ms",
                host, ranked.size(), reachable, elapsed);

        return ranked;
    }

    private List<OriginIpCandidate> validateAll(String host, List<HistoricalDnsRecord> records) {
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(validationParallelism, records.size()));

        List<CompletableFuture<OriginIpCandidate>> futures = new ArrayList<>();
        for (HistoricalDnsRecord rec : records) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> validateOne(host, rec), pool));
        }

        try {
            return futures.stream().map(this::joinSafely).toList();
        } finally {
            pool.shutdown();
        }
    }

    private OriginIpCandidate validateOne(String host, HistoricalDnsRecord rec) {
        OriginIpValidator.ValidationResult result = validator.validate(rec.ip(), host);
        double conf = computeConfidence(rec, result.status());
        return OriginIpCandidate.builder()
                .ip(rec.ip())
                .firstSeen(rec.firstSeen())
                .lastSeen(rec.lastSeen())
                .discoverySource(rec.source())
                .validationStatus(result.status())
                .validationHttpStatus(result.httpStatus())
                .validationLatencyMs(result.latencyMs())
                .confidence(conf)
                .build();
    }

    private OriginIpCandidate toUnvalidated(HistoricalDnsRecord rec) {
        return OriginIpCandidate.builder()
                .ip(rec.ip())
                .firstSeen(rec.firstSeen())
                .lastSeen(rec.lastSeen())
                .discoverySource(rec.source())
                .validationStatus(ValidationStatus.NOT_TESTED)
                .confidence(0.3)
                .build();
    }

    private OriginIpCandidate joinSafely(CompletableFuture<OriginIpCandidate> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[ORIGIN] validation future failed: {}", e.getMessage());
            return null;
        }
    }

    private int statusRank(ValidationStatus s) {
        return switch (s) {
            case REACHABLE     -> 0;
            case WRONG_CONTENT -> 1;
            case NOT_TESTED    -> 2;
            case TLS_ERROR     -> 3;
            case TIMEOUT       -> 4;
            case UNREACHABLE   -> 5;
        };
    }

    private double computeConfidence(HistoricalDnsRecord rec, ValidationStatus status) {
        double base = switch (status) {
            case REACHABLE     -> 0.95;
            case WRONG_CONTENT -> 0.55;
            case NOT_TESTED    -> 0.30;
            case TLS_ERROR     -> 0.20;
            case TIMEOUT       -> 0.10;
            case UNREACHABLE   -> 0.05;
        };
        if (rec.source().contains("+")) base = Math.min(1.0, base + 0.05);
        return Math.round(base * 100.0) / 100.0;
    }
}