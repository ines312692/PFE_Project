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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@ApplicationScoped
public class ProxyScrapeFetcher implements ProxySourceFetcher {

    private static final String API_URL =
            "https://api.proxyscrape.com/v3/free-proxy-list/get" +
                    "?request=displayproxies&proxy_format=ipport&format=json&timeout=10000";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PROXIES_PER_FETCH_DEFAULT = 200;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    @ConfigProperty(name = "proxy.fetch.proxyscrape.max-per-batch", defaultValue = "200")
    int maxPerBatch;

    @Override
    public ProxySource source() {
        return ProxySource.PROXYSCRAPE;
    }

    @Override
    public List<Proxy> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[PROXYSCRAPE] HTTP {} — body={}", response.statusCode(),
                        truncate(response.body()));
                return List.of();
            }
            return parse(response.body());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[PROXYSCRAPE] fetch interrupted");
            return List.of();
        } catch (Exception e) {
            log.warn("[PROXYSCRAPE] fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Proxy> parse(String body) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode list = root.has("proxies") ? root.get("proxies") : root;
            if (!list.isArray()) {
                log.warn("[PROXYSCRAPE] Unexpected response shape: {}", truncate(body));
                return List.of();
            }

            int cap = Math.min(maxPerBatch, MAX_PROXIES_PER_FETCH_DEFAULT);
            List<Proxy> result = new ArrayList<>(Math.min(cap, list.size()));

            for (int i = 0; i < list.size() && result.size() < cap; i++) {
                JsonNode node = list.get(i);
                toProxy(node).ifPresentOrElse(result::add, () -> {
                });
            }

            log.info("[PROXYSCRAPE] Parsed {} proxies (cap={})", result.size(), cap);
            return result;

        } catch (Exception e) {
            log.warn("[PROXYSCRAPE] parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private java.util.Optional<Proxy> toProxy(JsonNode node) {
        try {
            String ip = textOrNull(node, "ip", "host");
            Integer port = intOrNull(node);
            String protocolStr = textOrNull(node, "protocol", "type");
            if (ip == null || port == null || protocolStr == null) return java.util.Optional.empty();

            ProxyProtocol protocol = parseProtocol(protocolStr);
            if (protocol == null) return java.util.Optional.empty();

            return java.util.Optional.of(Proxy.builder()
                    .host(ip)
                    .port(port)
                    .protocol(protocol)
                    .country(CountryCodeNormalizer.normalize(textOrNull(node, "country", "country_code")))
                    .anonymity(parseAnonymity(textOrNull(node, "anonymity", "anonymityLevel")))
                    .source(ProxySource.PROXYSCRAPE)
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
        String upper = s.trim().toUpperCase();
        return switch (upper) {
            case "HTTP" -> ProxyProtocol.HTTP;
            case "HTTPS" -> ProxyProtocol.HTTPS;
            case "SOCKS4" -> ProxyProtocol.SOCKS4;
            case "SOCKS5" -> ProxyProtocol.SOCKS5;
            default -> null;
        };
    }

    private static ProxyAnonymity parseAnonymity(String s) {
        if (s == null) return ProxyAnonymity.UNKNOWN;
        String upper = s.trim().toUpperCase();
        return switch (upper) {
            case "ELITE", "HIGH" -> ProxyAnonymity.ELITE;
            case "ANONYMOUS" -> ProxyAnonymity.ANONYMOUS;
            case "TRANSPARENT" -> ProxyAnonymity.TRANSPARENT;
            default -> ProxyAnonymity.UNKNOWN;
        };
    }

    private static String textOrNull(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }

    private static Integer intOrNull(JsonNode node) {
        JsonNode v = node.get("port");
        if (v == null || v.isNull()) return null;
        return v.isInt() ? Integer.valueOf(v.asInt()) : tryParseInt(v.asText());
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}