package com.elyadata.feature.scraper.site.dns;

import com.elyadata.feature.scraper.site.detector.config.CloudflareAntiBotConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Slf4j
@ApplicationScoped
public class CdnIpRangeRegistry {

    private final CloudflareAntiBotConfig cloudflareConfig;

    @Inject
    public CdnIpRangeRegistry(CloudflareAntiBotConfig cloudflareConfig) {
        this.cloudflareConfig = cloudflareConfig;
    }

    public boolean isCdnIp(String ipString) {
        try {
            InetAddress ip = InetAddress.getByName(ipString);
            boolean ipv4 = ip instanceof Inet4Address;

            List<String> ranges = ipv4
                    ? cloudflareConfig.ipv4Ranges()
                    : cloudflareConfig.ipv6Ranges();

            return CidrMatcher.isInAnyRange(ip, ranges);
        } catch (UnknownHostException e) {
            log.debug("[CDN-FILTER] unparseable IP {} — treating as CDN to be safe", ipString);
            return true;
        }
    }
}