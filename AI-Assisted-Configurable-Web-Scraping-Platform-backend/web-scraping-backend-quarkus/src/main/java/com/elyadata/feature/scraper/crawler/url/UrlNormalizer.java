package com.elyadata.feature.scraper.crawler.url;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class UrlNormalizer {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "gclid", "fbclid", "mc_cid", "mc_eid", "_ga", "ref", "ref_src"
    );

    public static Optional<String> normalize(String raw, String baseUrl) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            URI base = URI.create(baseUrl);
            URI resolved = base.resolve(raw.trim()).normalize();

            String scheme = resolved.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return Optional.empty();
            }

            String host = resolved.getHost();
            if (host == null) return Optional.empty();
            host = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");

            int port = resolved.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }

            String path = resolved.getRawPath();
            if (path == null || path.isEmpty()) path = "/";

            String query = cleanQuery(resolved.getRawQuery());

            URI canonical = new URI(scheme, null, host, port, path, query, null);
            return Optional.of(canonical.toString());

        } catch (URISyntaxException | IllegalArgumentException e) {
            log.debug("[URL-NORM] Invalid URL '{}' from base '{}': {}", raw, baseUrl, e.getMessage());
            return Optional.empty();
        }
    }

    public static String extractDomain(String url) {
        try {
            return URI.create(url).getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    private static String cleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return null;

        TreeMap<String, String> sorted = new TreeMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            if (!TRACKING_PARAMS.contains(k.toLowerCase(Locale.ROOT))) {
                sorted.put(k, v);
            }
        }
        if (sorted.isEmpty()) return null;
        return sorted.entrySet().stream()
                .map(e -> e.getValue().isEmpty() ? e.getKey() : e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }
}