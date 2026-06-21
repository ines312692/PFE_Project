package com.elyadata.feature.scraper.site.detector.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;

@ConfigMapping(prefix = "scraper.antibot.cloudflare")
public interface CloudflareAntiBotConfig {

    List<String> ipv4Ranges();

    List<String> ipv6Ranges();
}