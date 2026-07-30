package com.elyadata.feature.scraper.crawler.discovery;

import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
public class SiteFileDiscoverer {

    private static final Map<String, String> KNOWN_PATHS = Map.ofEntries(
            Map.entry("/robots.txt", "ROBOTS_TXT"),
            Map.entry("/sitemap.xml", "SITEMAP"),
            Map.entry("/sitemap_index.xml", "SITEMAP"),
            Map.entry("/humans.txt", "HUMANS_TXT"),
            Map.entry("/.well-known/security.txt", "SECURITY"),
            Map.entry("/manifest.json", "MANIFEST"),
            Map.entry("/about", "ABOUT"),
            Map.entry("/about.html", "ABOUT"),
            Map.entry("/about-us", "ABOUT"),
            Map.entry("/a-propos", "ABOUT"),
            Map.entry("/contact", "CONTACT"),
            Map.entry("/contact.html", "CONTACT")
    );
    private static final Set<String> STORE_CONTENT =
            Set.of("ROBOTS_TXT", "SITEMAP", "HUMANS_TXT", "SECURITY", "MANIFEST");

    private static final int MAX_CONTENT_LENGTH = 8_000;

    public List<DiscoveredFile> discover(String targetUrl) {
        String base = extractBase(targetUrl);
        List<DiscoveredFile> found = new ArrayList<>();

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        for (Map.Entry<String, String> entry : KNOWN_PATHS.entrySet()) {
            String path = entry.getKey();
            String fileType = entry.getValue();
            String url = base + path;

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(6))
                        .header("User-Agent", "ElyaScraper/1.0")
                        .GET()
                        .build();

                HttpResponse<String> res =
                        client.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() < 400) {
                    String content = STORE_CONTENT.contains(fileType)
                            ? truncate(res.body())
                            : null;
                    found.add(new DiscoveredFile(url, fileType, res.statusCode(), content));
                    log.info("[DISCOVER] {} → {} ({})", fileType, url, res.statusCode());
                }

            } catch (Exception e) {
                log.debug("[DISCOVER] Unreachable: {} — {}", url, e.getMessage());
            }
        }

        log.info("[DISCOVER] {}/{} file(s) found for {}",
                found.size(), KNOWN_PATHS.size(), base);
        return found;
    }

    private static String extractBase(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }

    private static String truncate(String text) {
        return (text != null && text.length() > SiteFileDiscoverer.MAX_CONTENT_LENGTH) ? text.substring(0, SiteFileDiscoverer.MAX_CONTENT_LENGTH) : text;
    }
}