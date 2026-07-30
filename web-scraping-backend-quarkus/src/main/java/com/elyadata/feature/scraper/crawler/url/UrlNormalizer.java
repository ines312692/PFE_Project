package com.elyadata.feature.scraper.crawler.url;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
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
            URI resolved = URI.create(baseUrl).resolve(raw.trim()).normalize();

            String scheme = resolved.getScheme();
            if (scheme == null) return Optional.empty();
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return Optional.empty();

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
            StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
            if (port != -1) sb.append(':').append(port);
            sb.append(path);
            if (query != null) sb.append('?').append(query);

            return Optional.of(sb.toString());

        } catch (IllegalArgumentException e) {
            log.debug("[URL-NORM] Invalid URL '{}' from base '{}': {}", raw, baseUrl, e.getMessage());
            return Optional.empty();
        }
    }

    public static String extractDomain(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    private static String cleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return null;

        List<String[]> params = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            if (!TRACKING_PARAMS.contains(k.toLowerCase(Locale.ROOT))) {
                params.add(new String[]{k, v});
            }
        }
        if (params.isEmpty()) return null;

        params.sort(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]));
        return params.stream()
                .map(p -> p[1].isEmpty() ? p[0] : p[0] + "=" + p[1])
                .collect(Collectors.joining("&"));
    }
}