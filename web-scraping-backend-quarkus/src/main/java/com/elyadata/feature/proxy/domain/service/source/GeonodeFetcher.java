package com.elyadata.feature.proxy.domain.service.source;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.ProxySource;
import com.elyadata.feature.proxy.domain.model.ProxyStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@ApplicationScoped
public class GeonodeFetcher implements ProxySourceFetcher {

    private static final String API_URL =
            "https://proxylist.geonode.com/api/proxy-list" +
                    "?limit=500&page=1&sort_by=lastChecked&sort_type=desc";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public ProxySource source() {
        return ProxySource.GEONODE;
    }

    @Override
    public List<Proxy> fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[GEONODE] HTTP {}", res.statusCode());
                return List.of();
            }

            JsonNode data = json.readTree(res.body()).get("data");
            if (data == null || !data.isArray()) return List.of();

            List<Proxy> proxies = new ArrayList<>(data.size());
            for (JsonNode n : data) {
                toProxy(n).ifPresentOrElse(proxies::add, () -> {
                });
            }
            log.info("[GEONODE] Parsed {} proxies", proxies.size());
            return proxies;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[GEONODE] fetch interrupted");
            return List.of();
        } catch (Exception e) {
            log.warn("[GEONODE] fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    private java.util.Optional<Proxy> toProxy(JsonNode n) {
        try {
            String ip = n.get("ip").asText();
            int port = Integer.parseInt(n.get("port").asText());
            JsonNode protocols = n.get("protocols");
            if (protocols == null || !protocols.isArray() || protocols.isEmpty()) return java.util.Optional.empty();

            ProxyProtocol protocol = parseProtocol(protocols.get(0).asText());
            if (protocol == null) return java.util.Optional.empty();

            return java.util.Optional.of(Proxy.builder()
                    .host(ip)
                    .port(port)
                    .protocol(protocol)
                    .country(CountryCodeNormalizer.normalize(textOrNull(n, "country")))
                    .anonymity(parseAnonymity(textOrNull(n, "anonymityLevel")))
                    .source(ProxySource.GEONODE)
                    .status(ProxyStatus.ACTIVE)
                    .successRate(0.0)
                    .totalUses(0L)
                    .successfulUses(0L)
                    .build());

        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static ProxyProtocol parseProtocol(String s) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase()) {
            case "http" -> ProxyProtocol.HTTP;
            case "https" -> ProxyProtocol.HTTPS;
            case "socks4" -> ProxyProtocol.SOCKS4;
            case "socks5" -> ProxyProtocol.SOCKS5;
            default -> null;
        };
    }

    private static ProxyAnonymity parseAnonymity(String s) {
        if (s == null) return ProxyAnonymity.UNKNOWN;
        return switch (s.trim().toLowerCase()) {
            case "elite" -> ProxyAnonymity.ELITE;
            case "anonymous" -> ProxyAnonymity.ANONYMOUS;
            case "transparent" -> ProxyAnonymity.TRANSPARENT;
            default -> ProxyAnonymity.UNKNOWN;
        };
    }

    private static String textOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }
}