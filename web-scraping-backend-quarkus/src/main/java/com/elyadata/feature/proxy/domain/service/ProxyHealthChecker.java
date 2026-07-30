package com.elyadata.feature.proxy.domain.service;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyHealthCheck;
import com.elyadata.feature.proxy.domain.model.ProxyStatus;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;
import com.elyadata.feature.proxy.domain.service.antibot.ProxyAntiBotDetectorRegistry;
import com.elyadata.feature.proxy.domain.service.port.ProxyPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Predicate;

@Slf4j
@ApplicationScoped
public class ProxyHealthChecker {

    private final ProxyPort proxyPort;
    private final ProxyAntiBotDetectorRegistry detectorRegistry;

    @ConfigProperty(name = "proxy.health.connect-timeout-ms", defaultValue = "3000")
    int connectTimeoutMs;

    @ConfigProperty(name = "proxy.health.deactivate-after-failures", defaultValue = "5")
    int deactivateAfterFailures;

    @ConfigProperty(name = "proxy.health.parallelism", defaultValue = "20")
    int parallelism;

    public ProxyHealthChecker(ProxyPort proxyPort, ProxyAntiBotDetectorRegistry detectorRegistry) {
        this.proxyPort = proxyPort;
        this.detectorRegistry = detectorRegistry;
    }

    public ProxyHealthCheck check(Proxy proxy) {
        return doSocketCheck(proxy);
    }

    public ProxyHealthCheck checkFull(Proxy proxy) {
        return doFullCheck(proxy);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public DetectionResult checkAntiBot(Proxy proxy, AntiBotProvider provider) {
        DetectionResult r = detectorRegistry.check(proxy, provider);
        proxyPort.upsertAntiBotStatus(ProxyAntiBotStatus.fromDetection(proxy.id(), r));
        if (r.blocked()) {
            log.warn("[PROXY-AB] {} blocked by {} → BLACKLISTED", proxy.endpoint(), provider);
            proxyPort.updateStatus(proxy.id(), ProxyStatus.BLACKLISTED);
        }
        return r;
    }

    public int checkBatch(List<Proxy> proxies) {
        return runBatch(proxies, "proxy-health-checker",
                p -> doSocketCheck(p).success(),
                connectTimeoutMs + 2_000L).usable();
    }

    public record BatchResult(int total, int usable, int unusable) {
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProxyHealthCheck doSocketCheck(Proxy proxy) {
        long startNs = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(proxy.host(), proxy.port()), connectTimeoutMs);
            int latencyMs = (int) ((System.nanoTime() - startNs) / 1_000_000);

            ProxyHealthCheck check = ProxyHealthCheck.builder()
                    .proxyId(proxy.id())
                    .success(true)
                    .latencyMs(latencyMs)
                    .checkedAt(Instant.now())
                    .build();
            proxyPort.saveHealthCheck(check);
            proxyPort.release(proxy.id(), true, latencyMs);
            return check;

        } catch (Exception e) {
            ProxyHealthCheck check = ProxyHealthCheck.builder()
                    .proxyId(proxy.id())
                    .success(false)
                    .errorMessage(truncate(e.getClass().getSimpleName() + ": " + e.getMessage()))
                    .checkedAt(Instant.now())
                    .build();
            proxyPort.saveHealthCheck(check);
            proxyPort.release(proxy.id(), false, null);
            maybeDeactivate(proxy);
            return check;
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProxyHealthCheck doFullCheck(Proxy proxy) {
        ProxyHealthCheck socketResult = doSocketCheck(proxy);

        if (!socketResult.success()) {
            Instant now = Instant.now();
            for (AntiBotProvider provider : detectorRegistry.registeredProviders()) {
                proxyPort.upsertAntiBotStatus(ProxyAntiBotStatus.builder()
                        .proxyId(proxy.id())
                        .provider(provider)
                        .status(ProtectionStatus.UNREACHABLE)
                        .lastCheckedAt(now)
                        .lastError("Socket check failed")
                        .build());
            }
            return socketResult;
        }

        List<DetectionResult> results = detectorRegistry.checkAll(proxy);
        boolean anyBlocked = false;
        Integer maxLatency = socketResult.latencyMs();

        for (DetectionResult r : results) {
            proxyPort.upsertAntiBotStatus(ProxyAntiBotStatus.fromDetection(proxy.id(), r));
            if (r.blocked()) anyBlocked = true;
            if (r.latencyMs() != null && (maxLatency == null || r.latencyMs() > maxLatency)) {
                maxLatency = r.latencyMs();
            }
        }

        boolean isUsable = results.isEmpty() || results.stream().anyMatch(DetectionResult::isUsable);

        ProxyHealthCheck full = ProxyHealthCheck.builder()
                .proxyId(proxy.id())
                .success(isUsable)
                .latencyMs(maxLatency)
                .checkedAt(Instant.now())
                .build();
        proxyPort.saveHealthCheck(full);

        if (anyBlocked) {
            log.warn("[PROXY-AB] {} blocked by at least one anti-bot system → BLACKLISTED",
                    proxy.endpoint());
            proxyPort.updateStatus(proxy.id(), ProxyStatus.BLACKLISTED);
        }
        return full;
    }

    public BatchResult checkAntiBotBatch(AntiBotProvider provider, List<Proxy> proxies) {
        return runBatch(proxies, "proxy-ab-" + provider.name().toLowerCase(),
                p -> checkAntiBot(p, provider).isUsable(),
                connectTimeoutMs + 15_000L);
    }

    private BatchResult runBatch(List<Proxy> proxies, String threadName,
                                 Predicate<Proxy> task,
                                 long perTaskTimeoutMs) {
        if (proxies.isEmpty()) return new BatchResult(0, 0, 0);

        int threads = Math.min(parallelism, proxies.size());
        try (ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        })) {
            List<Future<Boolean>> futures = proxies.stream()
                    .map(p -> pool.submit(() -> {
                        try {
                            return task.test(p);
                        } catch (Exception e) {
                            log.debug("[PROXY-BATCH] crashed for {}: {}", p.endpoint(), e.getMessage());
                            return false;
                        }
                    }))
                    .toList();

            int ok = 0;
            int ko = 0;
            for (Future<Boolean> f : futures) {
                try {
                    if (Boolean.TRUE.equals(f.get(perTaskTimeoutMs, TimeUnit.MILLISECONDS))) ok++;
                    else ko++;
                } catch (TimeoutException te) {
                    f.cancel(true);
                    ko++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    futures.forEach(fut -> fut.cancel(true));
                    break;
                } catch (Exception e) {
                    log.debug("[PROXY-BATCH] future fetch error: {}", e.getMessage());
                    ko++;
                }
            }
            return new BatchResult(proxies.size(), ok, ko);
        }
    }

    private void maybeDeactivate(Proxy proxy) {
        long failures = (proxy.totalUses() == null ? 0 : proxy.totalUses())
                - (proxy.successfulUses() == null ? 0 : proxy.successfulUses());
        if (failures >= deactivateAfterFailures) {
            log.warn("[PROXY-HEALTH] deactivating {} after {} failures", proxy.endpoint(), failures);
            proxyPort.updateStatus(proxy.id(), ProxyStatus.INACTIVE);
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}