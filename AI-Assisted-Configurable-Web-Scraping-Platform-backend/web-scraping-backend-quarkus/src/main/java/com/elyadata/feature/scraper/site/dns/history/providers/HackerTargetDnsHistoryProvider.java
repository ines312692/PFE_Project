package com.elyadata.feature.scraper.site.dns.history.providers;

import com.elyadata.feature.scraper.site.dns.history.DnsHistoryProvider;
import com.elyadata.feature.scraper.site.model.HistoricalDnsRecord;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


@Slf4j
@ApplicationScoped
public class HackerTargetDnsHistoryProvider implements DnsHistoryProvider {

    private static final String NAME = "hackertarget";
    private static final String ENDPOINT = "https://api.hackertarget.com/hostsearch/?q=";
    private static final int MAX_RESULTS = 50;

    @ConfigProperty(name = "dns-history.hackertarget.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "dns-history.hackertarget.timeout-ms", defaultValue = "4000")
    int timeoutMs;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<HistoricalDnsRecord> fetchHistory(String host) {
        String registrable = toRegistrableDomain(host);
        URI uri = URI.create(ENDPOINT + registrable);

        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "ElyaScraper/1.0 (+dns-history-lookup)")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.debug("[{}] non-200 response for {}: status={}", NAME, registrable, resp.statusCode());
                return List.of();
            }

            String body = resp.body();
            if (body == null || body.isBlank() || body.toLowerCase().startsWith("error")) {
                log.debug("[{}] empty/error body for {}", NAME, registrable);
                return List.of();
            }

            return parseCsv(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("[{}] fetch interrupted for {}", NAME, registrable);
            return List.of();
        } catch (Exception e) {
            log.debug("[{}] fetch failed for {}: {}", NAME, registrable, e.getMessage());
            return List.of();
        }
    }

    private List<HistoricalDnsRecord> parseCsv(String body) {
        Set<String> seenIps = new LinkedHashSet<>();
        List<HistoricalDnsRecord> records = new ArrayList<>();

        for (String line : body.split("\\r?\\n")) {
            String[] parts = line.split(",");
            String ip = (parts.length >= 2) ? parts[1].trim() : "";

            boolean isValid = !line.isBlank() && !ip.isEmpty() && looksLikeIp(ip) && seenIps.add(ip);
            if (isValid) {
                records.add(new HistoricalDnsRecord(ip, null, null, NAME));
            }

            if (records.size() >= MAX_RESULTS) break;
        }

        return records;
    }

    private static boolean looksLikeIp(String s) {
        return s.matches("^[0-9a-fA-F:.]+$") && (s.contains(".") || s.contains(":"));
    }

    private static String toRegistrableDomain(String host) {
        return host.toLowerCase().startsWith("www.") ? host.substring(4) : host;
    }
}