package com.elyadata.feature.proxy.domain.service.antibot.detectors;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.antibot.*;
import com.elyadata.feature.proxy.domain.service.antibot.ProxyAntiBotDetector;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.elyadata.feature.proxy.domain.service.antibot.detectors.DataDomeProxyDetector.getProtectionStatus;

@Slf4j
@ApplicationScoped
public class CloudflareProxyDetector implements ProxyAntiBotDetector {

    private static final String TEST_URL_HTTP  = "http://www.cloudflare.com/cdn-cgi/trace";
    private static final String TEST_URL_HTTPS = "https://www.cloudflare.com/cdn-cgi/trace";

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final List<String> CF_HEADER_MARKERS =
            List.of("cf-ray", "cf-cache-status", "cf-request-id", "cf-bgj");


    private static final List<String> BLOCK_BODY_MARKERS = List.of(
            "Just a moment...", "cf-challenge", "__cf_chl_",
            "Attention Required", "Sorry, you have been blocked",
            "Error 1020", "cf-error-details"
    );

    @ConfigProperty(name = "proxy.antibot.cloudflare.timeout-ms", defaultValue = "5000")
    int timeoutMs;

    @Override
    public AntiBotProvider provider() {
        return AntiBotProvider.CLOUDFLARE;
    }

    @Override
    public DetectionResult check(Proxy proxy) {
        if (proxy.protocol() == ProxyProtocol.SOCKS4 || proxy.protocol() == ProxyProtocol.SOCKS5) {
            log.warn("[CF-DETECT] SOCKS not supported by JDK HttpClient — {}", proxy.endpoint());
            return DetectionResult.untested(provider(),
                    "SOCKS proxies are not supported by JDK HttpClient");
        }

        DetectionResult httpResult = doRequest(proxy, TEST_URL_HTTP);
        if (httpResult.status() != ProtectionStatus.UNREACHABLE) return httpResult;

        log.debug("[CF-DETECT] {} HTTP unreachable → falling back to HTTPS", proxy.endpoint());
        return doRequest(proxy, TEST_URL_HTTPS);
    }

    private DetectionResult doRequest(Proxy proxy, String url) {
        long startNs = System.nanoTime();
        String scheme = url.startsWith("https") ? "HTTPS" : "HTTP";
        try {
            HttpClient client = buildClient(proxy);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", DEFAULT_UA)
                    .header("Accept", "text/html,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int latencyMs = (int) ((System.nanoTime() - startNs) / 1_000_000);
            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            Map<String, List<String>> headers = response.headers().map();

            boolean cfDetected = isCloudflareDetected(headers);
            boolean cfBlocked = isCloudflareBlocked(status, body);
            ProtectionStatus protectionStatus = classify(status, cfDetected, cfBlocked);

            log.debug("[CF-DETECT] {} ({}) → status={} detected={} blocked={} → {}",
                    proxy.endpoint(), scheme, status, cfDetected, cfBlocked, protectionStatus);

            return new DetectionResult(provider(), protectionStatus,
                    status, cfDetected, cfBlocked, latencyMs, Instant.now(), null);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.debug("[CF-DETECT] {} ({}) failed: {}", proxy.endpoint(), scheme, e.getMessage());
            return DetectionResult.unreachable(provider(),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private HttpClient buildClient(Proxy proxy) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));

        if (proxy.hasCredentials()) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxy.username(),
                            proxy.password() == null ? new char[0] : proxy.password().toCharArray());
                }
            });
        }
        return builder.build();
    }

    private boolean isCloudflareDetected(Map<String, List<String>> headers) {
        for (var entry : headers.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if ("server".equals(key)
                    && entry.getValue().stream().anyMatch(v -> v.toLowerCase().contains("cloudflare"))) return true;
            if (CF_HEADER_MARKERS.contains(key)) return true;
        }
        return false;
    }

    private boolean isCloudflareBlocked(int status, String body) {
        if (status == 403 || status == 503 || status == 429) return true;
        return BLOCK_BODY_MARKERS.stream().anyMatch(body::contains);
    }

    private ProtectionStatus classify(int status, boolean detected, boolean blocked) {
        return getProtectionStatus(status, detected, blocked);
    }
}