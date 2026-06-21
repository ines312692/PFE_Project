package com.elyadata.feature.scraper.site.detector.detectors;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotMarkers;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.detector.SiteProtectionDetector;
import com.elyadata.feature.scraper.site.detector.config.CloudflareAntiBotConfig;
import com.elyadata.feature.scraper.site.dns.CidrMatcher;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import com.elyadata.feature.scraper.site.model.SiteDnsProbe;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CloudflareSiteDetector implements SiteProtectionDetector {

    private final CloudflareAntiBotConfig cloudflareAntiBotConfig;

    @Override
    public AntiBotProvider provider() {
        return AntiBotProvider.CLOUDFLARE;
    }

    @Override
    public Optional<DetectedProtection> fingerprint(int status,
                                                    Map<String, List<String>> headers,
                                                    String body) {
        String matchedHeader = findHeader(headers);
        if (matchedHeader != null) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.98,
                    "header '" + matchedHeader + "' present"));
        }

        if (serverIsCloudflare(headers)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.95, "server header indicates cloudflare"));
        }

        String lowerBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (AntiBotMarkers.Cloudflare.BODY_BLOCK_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.90, "cloudflare challenge/block page detected"));
        }
        if (AntiBotMarkers.Cloudflare.BODY_DETECT_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.55, "cloudflare mentioned in page body"));
        }

        return Optional.empty();
    }

    @Override
    public Optional<DetectedProtection> dnsFingerprint(SiteDnsProbe dns) {
        if (dns == null || dns.isEmpty()) {
            log.debug("[CF-DNS] no DNS data — skipping DNS fingerprint");
            return Optional.empty();
        }

        log.debug("[CF-DNS]  checking host={} — {} ip(s), {} ns",
                dns.host(), dns.resolvedIps().size(), dns.authoritativeNameservers().size());

        Optional<MatchedIp> match = findMatchingIp(dns.resolvedIps());
        if (match.isPresent()) {
            MatchedIp m = match.get();
            log.info("[CF-DNS] ✓ host={} → IP {} matches Cloudflare range {}",
                    dns.host(), m.ip().getHostAddress(), m.cidr());
            return Optional.of(new DetectedProtection(
                    provider(), 0.99,
                    "resolved IP in Cloudflare range " + m.cidr()));
        }


        Optional<String> matchedNs = dns.authoritativeNameservers().stream()
                .filter(ns -> ns.endsWith(AntiBotMarkers.Cloudflare.NS_SUFFIX))
                .findFirst();
        if (matchedNs.isPresent()) {
            log.info("[CF-DNS] ✓ host={} → authoritative NS '{}' is Cloudflare (DNS-only)",
                    dns.host(), matchedNs.get());
            return Optional.of(new DetectedProtection(
                    provider(), 0.70,
                    "authoritative NS is Cloudflare (" + matchedNs.get()
                            + ") — DNS-only, no proxy detected"));
        }

        log.debug("[CF-DNS] ✗ host={} → no Cloudflare DNS signal", dns.host());
        return Optional.empty();
    }

    private Optional<MatchedIp> findMatchingIp(List<InetAddress> ips) {
        for (InetAddress ip : ips) {
            boolean isV4 = ip instanceof Inet4Address;
            List<String> ranges = isV4
                    ? cloudflareAntiBotConfig.ipv4Ranges()
                    : cloudflareAntiBotConfig.ipv6Ranges();

            log.trace("[CF-DNS] testing IP {} ({}) against {} CIDR range(s)",
                    ip.getHostAddress(), isV4 ? "IPv4" : "IPv6", ranges.size());

            for (String cidr : ranges) {
                if (CidrMatcher.isInRange(ip, cidr)) {
                    return Optional.of(new MatchedIp(ip, cidr));
                }
            }
            log.trace("[CF-DNS] IP {} not in any Cloudflare range", ip.getHostAddress());
        }
        return Optional.empty();
    }

    private String findHeader(Map<String, List<String>> headers) {
        for (String key : headers.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (AntiBotMarkers.Cloudflare.HEADERS.contains(lower)) return lower;
        }
        return null;
    }

    private boolean serverIsCloudflare(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(e -> "server".equalsIgnoreCase(e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .map(v -> v.toLowerCase(Locale.ROOT))
                .anyMatch(v -> AntiBotMarkers.Cloudflare.SERVER_VALUES.stream().anyMatch(v::contains));
    }

    private record MatchedIp(InetAddress ip, String cidr) {
    }
}