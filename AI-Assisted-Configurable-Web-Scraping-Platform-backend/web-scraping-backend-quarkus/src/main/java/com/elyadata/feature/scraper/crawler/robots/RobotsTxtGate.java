package com.elyadata.feature.scraper.crawler.robots;

import com.elyadata.feature.scraper.crawler.url.UrlNormalizer;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class RobotsTxtGate {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final Map<String, List<String>> disallowsByDomain = new ConcurrentHashMap<>();

    public boolean isAllowed(String url) {
        String domain = UrlNormalizer.extractDomain(url);
        List<String> disallows = disallowsByDomain.computeIfAbsent(domain, d -> fetch(url));
        String path = URI.create(url).getRawPath();
        return disallows.stream().noneMatch(path::startsWith);
    }

    private List<String> fetch(String url) {
        try {
            URI robots = URI.create(url).resolve("/robots.txt");
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(robots).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.debug("[ROBOTS] {} → {} (no robots.txt, allowing all)", robots, resp.statusCode());
                return List.of();
            }
            return parseDisallows(resp.body());
        } catch (Exception e) {
            log.debug("[ROBOTS] Fetch failed for {} — allowing all: {}", url, e.getMessage());
            return List.of();
        }
    }

    private static List<String> parseDisallows(String body) {
        List<String> out = new ArrayList<>();
        boolean inWildcardBlock = false;

        for (String raw : body.split("\\r?\\n")) {
            String line = raw.trim();

            if (!line.isEmpty() && !line.startsWith("#")) {
                int colon = line.indexOf(':');

                if (colon >= 0) {
                    String key = line.substring(0, colon).trim().toLowerCase();
                    String val = line.substring(colon + 1).trim();

                    if (key.equals("user-agent")) {
                        inWildcardBlock = val.equals("*");
                    } else if (inWildcardBlock && key.equals("disallow") && !val.isEmpty()) {
                        out.add(val);
                    }
                }
            }
        }

        return out;
    }
}