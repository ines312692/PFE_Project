package com.elyadata.feature.scraper.browser.stealth;

import lombok.Builder;

import java.util.List;


@Builder
public record ConsistencyProfile(
        String userAgent,
        String platform,
        String acceptLanguage,
        String language,
        List<String> languages,
        String timezone,
        String locale,
        String webglVendor,
        String webglRenderer,
        int hardwareConcurrency,
        int deviceMemory,
        int maxTouchPoints,
        String vendor,
        int screenWidth,
        int screenHeight,
        int screenAvailWidth,
        int screenAvailHeight,
        int viewportWidth,
        int viewportHeight
) {
}