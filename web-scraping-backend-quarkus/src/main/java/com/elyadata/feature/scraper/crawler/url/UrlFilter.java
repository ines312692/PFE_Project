package com.elyadata.feature.scraper.crawler.url;
import com.elyadata.feature.scraper.security.SsrfGuard;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
public final class UrlFilter {

    private final List<Pattern> includePatterns;
    private final List<Pattern> excludePatterns;
    private final String rootHost;
    private final boolean sameDomainOnly;
    private final SsrfGuard ssrfGuard;

    public UrlFilter(InternalScrapeRequest request) {
        this(request, null);
    }

    public UrlFilter(InternalScrapeRequest request, SsrfGuard ssrfGuard) {
        this.includePatterns = compile(request.getUrlIncludePatterns(), "include");
        this.excludePatterns = compile(request.getUrlExcludePatterns(), "exclude");
        this.rootHost = extractHost(request.getTargetUrl());
        this.sameDomainOnly = request.isSameDomainOnly();
        this.ssrfGuard = ssrfGuard;
    }


    public boolean accepts(String url) {
        if (sameDomainOnly && !extractHost(url).equals(rootHost)) {
            log.trace("[URL-FILTER] reject (off-domain): {}", url);
            return false;
        }
        if (!includePatterns.isEmpty()
                && includePatterns.stream().noneMatch(p -> p.matcher(url).find())) {
            log.trace("[URL-FILTER] reject (no include match): {}", url);
            return false;
        }
        if (excludePatterns.stream().anyMatch(p -> p.matcher(url).find())) {
            log.trace("[URL-FILTER] reject (exclude match): {}", url);
            return false;
        }
        if (ssrfGuard != null) {
            SsrfGuard.SsrfVerdict verdict = ssrfGuard.evaluate(url);
            if (!verdict.allowed()) {
                log.warn("[URL-FILTER] reject (SSRF): {} — {}", url, verdict.reason());
                return false;
            }
        }
        return true;
    }

    private static String extractHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return url;
            return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    private static List<Pattern> compile(String csv, String label) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        List<Pattern> out = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            try {
                String regex = isGlob(trimmed) ? globToRegex(trimmed) : trimmed;
                log.debug("[URL-FILTER] {} pattern: input='{}' → regex='{}'", label, trimmed, regex);
                out.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                log.warn("[URL-FILTER] Invalid {} pattern '{}' — skipped: {}", label, trimmed, e.getMessage());
            }
        }
        return out;
    }

    private static boolean isGlob(String pattern) {
        if (pattern.contains("\\") || pattern.startsWith("^") || pattern.contains("(?")) {
            return false;
        }
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if ((c == '*' || c == '?') && (i == 0 || pattern.charAt(i - 1) != '.')) {
                return true;
            }
        }
        return false;
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                sb.append(".*");
                i += 2;
                if (i < glob.length() && glob.charAt(i) == '/') i++;
            } else if (c == '*') {
                sb.append("[^/]*");
                i++;
            } else if (c == '?') {
                sb.append("[^/]");
                i++;
            } else if (c == '.') {
                sb.append("\\.");
                i++;
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return sb.toString();
    }
}