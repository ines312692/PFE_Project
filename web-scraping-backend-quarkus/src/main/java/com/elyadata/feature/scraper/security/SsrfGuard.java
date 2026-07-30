package com.elyadata.feature.scraper.security;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.scraper.site.dns.CidrMatcher;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@ApplicationScoped
public class SsrfGuard {

    private static final List<String> EXTRA_BLOCKED_V4 = List.of(
            "0.0.0.0/8",
            "100.64.0.0/10",
            "192.0.0.0/24",
            "192.0.2.0/24",
            "198.18.0.0/15",
            "198.51.100.0/24",
            "203.0.113.0/24",
            "240.0.0.0/4"
    );
    private static final List<String> EXTRA_BLOCKED_V6 = List.of(
            "fc00::/7",
            "2001:db8::/32"
    );

    private static final int MAX_CACHE_ENTRIES = 10_000;

    @ConfigProperty(name = "scraper.security.ssrf.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "scraper.security.ssrf.allow-private-networks", defaultValue = "false")
    boolean allowPrivateNetworks;

    @ConfigProperty(name = "scraper.security.ssrf.blocked-ports",
            defaultValue = "22,23,25,110,135,139,445,1433,3306,3389,5432,5900,6379,9200,11211,27017")
    String blockedPortsCsv;

    @ConfigProperty(name = "scraper.security.ssrf.dns-cache-ttl-seconds", defaultValue = "300")
    long dnsCacheTtlSeconds;

    private volatile Set<Integer> blockedPorts;
    private final Map<String, CacheEntry> dnsCache = new ConcurrentHashMap<>();

    public boolean isAllowed(String url) {
        return evaluate(url).allowed();
    }

    public void assertAllowed(String url) {
        SsrfVerdict verdict = evaluate(url);
        if (!verdict.allowed()) {
            throw DomainException.notValid("Request blocked by SSRF guard: " + verdict.reason());
        }
    }

    public SsrfVerdict evaluate(String url) {
        if (!enabled) {
            return SsrfVerdict.allow();
        }
        if (url == null || url.isBlank()) {
            return SsrfVerdict.block("empty URL");
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return SsrfVerdict.block("malformed URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return SsrfVerdict.block("scheme not allowed: " + scheme);
        }

        String host = stripBrackets(uri.getHost());
        if (host == null || host.isBlank()) {
            return SsrfVerdict.block("missing host");
        }

        int port = uri.getPort();
        if (port != -1 && blockedPorts().contains(port)) {
            return SsrfVerdict.block("port blocked: " + port);
        }

        InetAddress[] addresses;
        try {
            addresses = resolve(host);
        } catch (UnknownHostException e) {
            return SsrfVerdict.block("DNS resolution failed for host: " + host);
        }
        if (addresses.length == 0) {
            return SsrfVerdict.block("no A/AAAA records for host: " + host);
        }

        if (!allowPrivateNetworks) {
            for (InetAddress ip : addresses) {
                if (isInternal(ip)) {
                    return SsrfVerdict.block(
                            host + " resolves to internal/reserved address " + ip.getHostAddress());
                }
            }
        }
        return SsrfVerdict.allow();
    }

    private boolean isInternal(InetAddress address) {
        InetAddress ip = unwrapIpv4Mapped(address);
        if (ip.isAnyLocalAddress()
                || ip.isLoopbackAddress()
                || ip.isLinkLocalAddress()
                || ip.isSiteLocalAddress()
                || ip.isMulticastAddress()) {
            return true;
        }
        List<String> extra = (ip instanceof Inet4Address) ? EXTRA_BLOCKED_V4 : EXTRA_BLOCKED_V6;
        return CidrMatcher.isInAnyRange(ip, extra);
    }

    private static InetAddress unwrapIpv4Mapped(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                try {
                    return InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]});
                } catch (UnknownHostException ignored) {
                    // 4 bytes is always valid; keep original on the impossible failure
                }
            }
        }
        return address;
    }

    private InetAddress[] resolve(String host) throws UnknownHostException {
        long now = System.currentTimeMillis();
        CacheEntry cached = dnsCache.get(host);
        if (cached != null && cached.expiresAt() > now) {
            return cached.addresses();
        }
        InetAddress[] resolved = InetAddress.getAllByName(host);
        if (dnsCache.size() >= MAX_CACHE_ENTRIES) {
            dnsCache.clear();
        }
        dnsCache.put(host, new CacheEntry(resolved, now + dnsCacheTtlSeconds * 1_000L));
        return resolved;
    }

    private Set<Integer> blockedPorts() {
        Set<Integer> local = blockedPorts;
        if (local == null) {
            local = parsePorts(blockedPortsCsv);
            blockedPorts = local;
        }
        return local;
    }

    private static Set<Integer> parsePorts(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<Integer> out = new HashSet<>();
        for (String token : csv.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(Integer.parseInt(t));
            } catch (NumberFormatException e) {
                log.warn("[SSRF] Ignoring invalid blocked port '{}'", t);
            }
        }
        return Set.copyOf(out);
    }

    private static String stripBrackets(String host) {
        if (host == null) {
            return null;
        }
        if (host.length() > 1 && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private record CacheEntry(InetAddress[] addresses, long expiresAt) {
    }

    public record SsrfVerdict(boolean allowed, String reason) {
        public static SsrfVerdict allow() {
            return new SsrfVerdict(true, null);
        }

        public static SsrfVerdict block(String reason) {
            return new SsrfVerdict(false, reason);
        }
    }
}
