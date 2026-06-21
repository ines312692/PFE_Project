package com.elyadata.feature.scraper.site.dns.history;

import com.elyadata.feature.scraper.site.model.HistoricalDnsRecord;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Slf4j
@ApplicationScoped
public class DnsHistoryProviderRegistry {


    private final Instance<DnsHistoryProvider> providerInstances;

    private List<DnsHistoryProvider> orderedProviders;

    public DnsHistoryProviderRegistry(Instance<DnsHistoryProvider> providerInstances) {
        this.providerInstances = providerInstances;
    }

    @PostConstruct
    void init() {
        orderedProviders = StreamSupport.stream(providerInstances.spliterator(), false)
                .sorted(Comparator.comparingInt(DnsHistoryProvider::priority))
                .toList();

        List<String> enabled = orderedProviders.stream()
                .filter(DnsHistoryProvider::isEnabled)
                .map(DnsHistoryProvider::name)
                .toList();

        log.info("[DNS-HISTORY] Registered {} provider(s), {} enabled: {}",
                orderedProviders.size(), enabled.size(), enabled);
    }

    public List<HistoricalDnsRecord> aggregateHistory(String host) {
        if (host == null || host.isBlank()) return List.of();

        Map<String, HistoricalDnsRecord> deduped = new LinkedHashMap<>();

        for (DnsHistoryProvider provider : orderedProviders) {
            if (!provider.isEnabled()) continue;

            try {
                List<HistoricalDnsRecord> records = provider.fetchHistory(host);
                log.debug("[DNS-HISTORY] {} → {} record(s) for {}",
                        provider.name(), records.size(), host);

                for (HistoricalDnsRecord rec : records) {
                    deduped.merge(rec.ip(), rec, this::mergeRecords);
                }
            } catch (Exception e) {
                log.warn("[DNS-HISTORY] {} crashed for {}: {}",
                        provider.name(), host, e.getMessage());
            }
        }

        return new ArrayList<>(deduped.values());
    }

    private HistoricalDnsRecord mergeRecords(HistoricalDnsRecord a, HistoricalDnsRecord b) {
        var first = earliest(a.firstSeen(), b.firstSeen());
        var last = latest(a.lastSeen(), b.lastSeen());
        String source = a.source().equals(b.source()) ? a.source() : a.source() + "+" + b.source();
        return new HistoricalDnsRecord(a.ip(), first, last, source);
    }

    private static java.time.Instant earliest(java.time.Instant a, java.time.Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static java.time.Instant latest(java.time.Instant a, java.time.Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}