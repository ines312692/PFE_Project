package com.elyadata.feature.scraper.site.model;

import java.net.InetAddress;
import java.util.List;


public record SiteDnsProbe(
        String host,
        List<InetAddress> resolvedIps,
        List<String> authoritativeNameservers
) {
    public static SiteDnsProbe empty(String host) {
        return new SiteDnsProbe(host, List.of(), List.of());
    }

    public boolean isEmpty() {
        return resolvedIps.isEmpty() && authoritativeNameservers.isEmpty();
    }
}