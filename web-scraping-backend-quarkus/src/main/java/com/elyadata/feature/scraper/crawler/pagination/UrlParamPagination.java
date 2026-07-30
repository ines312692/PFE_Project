package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class UrlParamPagination implements PaginationStrategy {

    private static final int WAIT_AFTER_NAV_MS = 2_500;
    private static final String DEFAULT_PARAM = "p";
    private static final String[] COMMON_PARAMS = {"page", "p", "pg", "offset", "start", "from"};

    @Override
    public PaginationType type() {
        return PaginationType.URL_PARAM;
    }

    @Override
    public boolean hasNextPage(PageSnapshot snapshot, PaginationContext context) {
        if (context.hasReachedMaxPages()) {
            log.debug("[Pagination/URL_PARAM] reached max pages = {}", context.getMaxPages());
            return false;
        }
        if (context.isStale()) {
            log.debug("[Pagination/URL_PARAM] context is stale");
            return false;
        }
        return true;
    }

    @Override
    public void goToNextPage(ABrowser browser, PaginationContext context) {
        String currentUrl = extractCurrentUrl(browser);
        if (currentUrl == null) {
            log.warn("[Pagination/URL_PARAM] cannot get current URL — aborting");
            return;
        }

        String paramName = effectiveParam(context);
        int nextPage = context.getCurrentPage() + 1;
        String nextUrl = buildUrlWithParam(currentUrl, paramName, nextPage);

        log.info("[Pagination/URL_PARAM] navigating → page {} via {} (param='{}')",
                nextPage, nextUrl, paramName);

        browser.navigate(nextUrl);
        sleep();
    }
    @Override
    public DetectionResult detect(PageSnapshot snapshot) {
        String url = snapshot.getUrl();
        if (url == null) return DetectionResult.notFound(type());

        for (String param : COMMON_PARAMS) {
            if (url.matches(".*[?&]" + param + "=\\d+.*")) {
                return new DetectionResult(
                        PaginationType.URL_PARAM,
                        0.95,
                        "URL contains ?" + param + "=N",
                        param
                );
            }
        }
        String html = snapshot.getHtml();
        if (html != null) {
            for (String param : COMMON_PARAMS) {
                Pattern p = Pattern.compile("href=[\"'][^\"']*[?&]" + param + "=\\d+");
                if (p.matcher(html).find()) {
                    return new DetectionResult(
                            PaginationType.URL_PARAM,
                            0.80,
                            "outgoing links use ?" + param + "=N",
                            param
                    );
                }
            }
        }

        return DetectionResult.notFound(type());
    }

    private String extractCurrentUrl(ABrowser browser) {
        try {
            PageSnapshot snap = browser.snapshot();
            return snap.getUrl();
        } catch (Exception e) {
            log.warn("[Pagination/URL_PARAM] failed to read current URL: {}", e.getMessage());
            return null;
        }
    }

    private String effectiveParam(PaginationContext context) {
        String custom = context.getSelector();
        return (custom == null || custom.isBlank()) ? DEFAULT_PARAM : custom.trim();
    }

    static String buildUrlWithParam(String url, String param, int value) {
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();

            String paramRegex = "(^|&)" + Pattern.quote(param) + "=[^&]*";
            String newPair = param + "=" + value;

            String newQuery;
            if (query == null || query.isEmpty()) {
                newQuery = newPair;
            } else if (Pattern.compile(paramRegex).matcher(query).find()) {
                newQuery = query.replaceAll(paramRegex, "$1" + Matcher.quoteReplacement(newPair));
            } else {
                newQuery = query + "&" + newPair;
            }

            URI rebuilt = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    newQuery,
                    uri.getFragment()
            );
            return rebuilt.toString();
        } catch (URISyntaxException e) {
            log.warn("[Pagination/URL_PARAM] URI parse error on '{}': {}", url, e.getMessage());
            String sep = url.contains("?") ? "&" : "?";
            return url + sep + param + "=" + value;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(UrlParamPagination.WAIT_AFTER_NAV_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}