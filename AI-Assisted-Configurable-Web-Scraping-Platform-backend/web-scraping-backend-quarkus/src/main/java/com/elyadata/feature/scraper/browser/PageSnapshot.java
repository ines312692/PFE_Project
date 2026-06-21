package com.elyadata.feature.scraper.browser;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PageSnapshot {

    String url;
    String html;
    String text;
    LiveBrowserContext browserContext;
}