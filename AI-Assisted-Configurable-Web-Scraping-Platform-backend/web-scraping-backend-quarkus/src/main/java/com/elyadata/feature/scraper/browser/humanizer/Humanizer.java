package com.elyadata.feature.scraper.browser.humanizer;

import com.microsoft.playwright.Page;


public interface Humanizer {

    void afterPageLoad(Page page, HumanizerLevel level);

    void beforeClick(Page page, String selector, HumanizerLevel level);

    void scroll(Page page, boolean toBottom, HumanizerLevel level);

    void reset();
}