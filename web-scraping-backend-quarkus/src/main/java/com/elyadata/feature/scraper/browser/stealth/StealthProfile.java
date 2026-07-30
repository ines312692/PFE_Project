package com.elyadata.feature.scraper.browser.stealth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;


public interface StealthProfile {

    void prepareContextOptions(Browser.NewContextOptions options, ConsistencyProfile profile);

    void applyInitScripts(BrowserContext context, ConsistencyProfile profile);
}