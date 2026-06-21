package com.elyadata.feature.proxy.domain.service.antibot.detectors;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.antibot.*;
import com.elyadata.feature.proxy.domain.service.antibot.ProxyAntiBotDetector;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class DataDomeProxyDetector implements ProxyAntiBotDetector {

    private static final String TEST_URL = "https://js.datadome.co/tags.js";

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final List<String> DD_HEADER_MARKERS = List.of(
            "x-datadome", "x-datadome-cid", "x-dd-b", "server-timing"
    );

    private static final List<String> DD_BODY_MARKERS = List.of(
            "datadome", "dd_cookie_test", "geo.captcha-delivery.com",
            "Please complete the security check", "/captcha-delivery"
    );

    @ConfigProperty(name = "proxy.antibot.datadome.timeout-ms", defaultValue = "5000")
    int timeoutMs;

    @Override
    public AntiBotProvider provider() {
        return AntiBotProvider.DATADOME;
    }

    @Override
    public DetectionResult check(Proxy proxy) {
        if (proxy.protocol() == ProxyProtocol.SOCKS4 || proxy.protocol() == ProxyProtocol.SOCKS5) {
            return DetectionResult.untested(provider(),
                    "SOCKS proxies not supported by JDK HttpClient");
        }

        long startNs = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TEST_URL))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", DEFAULT_UA)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int latencyMs = (int) ((System.nanoTime() - startNs) / 1_000_000);

            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body().toLowerCase();
            Map<String, List<String>> headers = response.headers().map();
            boolean detected = headersContain(headers)
                    || DD_BODY_MARKERS.stream().anyMatch(body::contains);
            boolean blocked = status == 403 || status == 429
                    || body.contains("captcha-delivery")
                    || body.contains("blocked");

            ProtectionStatus s = classify(status, detected, blocked);
            log.debug("[DD-DETECT] {} → status={} detected={} blocked={} → {}",
                    proxy.endpoint(), status, detected, blocked, s);

            return new DetectionResult(provider(), s, status,
                    detected, blocked, latencyMs, Instant.now(), null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DetectionResult.unreachable(provider(),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (IOException e) {
            return DetectionResult.unreachable(provider(),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean headersContain(Map<String, List<String>> headers) {
        return headers.keySet().stream()
                .map(String::toLowerCase)
                .anyMatch(DD_HEADER_MARKERS::contains);
    }

    private ProtectionStatus classify(int status, boolean detected, boolean blocked) {
        return getProtectionStatus(status, detected, blocked);
    }

    @NonNull
    static ProtectionStatus getProtectionStatus(int status, boolean detected, boolean blocked) {
        if (blocked) return ProtectionStatus.BLOCKED;
        if (detected && status >= 200 && status < 300) return ProtectionStatus.CLEAN;
        if (detected) return ProtectionStatus.CHALLENGED;
        if (status >= 200 && status < 300) return ProtectionStatus.CLEAN;
        return ProtectionStatus.UNTESTED;
    }
}