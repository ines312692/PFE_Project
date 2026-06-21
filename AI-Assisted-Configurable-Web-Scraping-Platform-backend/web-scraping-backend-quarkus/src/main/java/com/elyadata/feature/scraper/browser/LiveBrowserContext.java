package com.elyadata.feature.scraper.browser;

import com.microsoft.playwright.Page;
import lombok.Builder;
import lombok.Value;
import org.openqa.selenium.WebDriver;

@Value
@Builder
public class LiveBrowserContext {

    Page page;
    WebDriver driver;

    public boolean isPlaywright() {
        return page != null;
    }

    public boolean isSelenium() {
        return driver != null;
    }
}