package com.elyadata.feature.proxy.domain.service.source;

import com.elyadata.feature.proxy.domain.model.Proxy;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class ProxySourceRegistry {


    private final Instance<ProxySourceFetcher> fetchers;

    private List<ProxySourceFetcher> ordered;

    public ProxySourceRegistry(Instance<ProxySourceFetcher> fetchers) {
        this.fetchers = fetchers;
    }

    @PostConstruct
    void init() {
        ordered = new ArrayList<>();
        for (ProxySourceFetcher f : fetchers) ordered.add(f);
        log.info("[PROXY-SOURCES] Registered {} fetchers: {}",
                ordered.size(), ordered.stream().map(f -> f.source().name()).toList());
    }

    public List<Proxy> fetchAll() {
        List<Proxy> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ProxySourceFetcher f : ordered) {
            try {
                List<Proxy> batch = f.fetch();
                int added = 0;
                for (Proxy p : batch) {
                    String key = p.host() + ":" + p.port() + ":" + p.protocol();
                    if (seen.add(key)) {
                        merged.add(p);
                        added++;
                    }
                }
                log.info("[PROXY-SOURCES] {} contributed {} new proxies (total {} fetched)",
                        f.source(), added, batch.size());
            } catch (Exception e) {
                log.warn("[PROXY-SOURCES] {} fetch crashed: {}", f.source(), e.getMessage());
            }
        }
        log.info("[PROXY-SOURCES] Merged total: {} unique proxies", merged.size());
        return merged;
    }
}
