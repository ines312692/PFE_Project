package com.elyadata.feature.proxy.domain.service.source;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.ProxySource;
import com.elyadata.feature.proxy.domain.model.ProxyStatus;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@ApplicationScoped
public class FreeProxyListFetcher implements ProxySourceFetcher {

    private static final String LIST_URL = "https://free-proxy-list.net/";
    private static final int FETCH_TIMEOUT_MS = (int) Duration.ofSeconds(20).toMillis();
    private static final int EXPECTED_COLUMNS = 8;

    @Override
    public ProxySource source() {
        return ProxySource.FREE_PROXY_LIST;
    }

    @Override
    public List<Proxy> fetch() {
        try {
            Document doc = Jsoup.connect(LIST_URL)
                    .userAgent("Mozilla/5.0 (compatible; ElyadataScraper/1.0)")
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();

            Elements rows = doc.select("table.table tbody tr");
            List<Proxy> proxies = new ArrayList<>(rows.size());
            for (Element row : rows) {
                parseRow(row).ifPresentOrElse(proxies::add, () -> {
                });
            }
            log.info("[FREE-PROXY-LIST] Parsed {} proxies", proxies.size());
            return proxies;

        } catch (Exception e) {
            log.warn("[FREE-PROXY-LIST] fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    private java.util.Optional<Proxy> parseRow(Element row) {
        try {
            Elements cells = row.select("td");
            if (cells.size() < EXPECTED_COLUMNS) return java.util.Optional.empty();

            String ip = cells.get(0).text().trim();
            int port = Integer.parseInt(cells.get(1).text().trim());
            String country = cells.get(2).text().trim();
            String anon = cells.get(4).text().trim();
            boolean https = "yes".equalsIgnoreCase(cells.get(6).text().trim());

            return java.util.Optional.of(Proxy.builder()
                    .host(ip)
                    .port(port)
                    .protocol(https ? ProxyProtocol.HTTPS : ProxyProtocol.HTTP)
                    .country(CountryCodeNormalizer.normalize(country))
                    .anonymity(parseAnonymity(anon))
                    .source(ProxySource.FREE_PROXY_LIST)
                    .status(ProxyStatus.ACTIVE)
                    .successRate(0.0)
                    .totalUses(0L)
                    .successfulUses(0L)
                    .build());

        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static ProxyAnonymity parseAnonymity(String s) {
        if (s == null) return ProxyAnonymity.UNKNOWN;
        String l = s.toLowerCase();
        if (l.contains("elite")) return ProxyAnonymity.ELITE;
        if (l.contains("anonymous")) return ProxyAnonymity.ANONYMOUS;
        if (l.contains("transparent")) return ProxyAnonymity.TRANSPARENT;
        return ProxyAnonymity.UNKNOWN;
    }
}
