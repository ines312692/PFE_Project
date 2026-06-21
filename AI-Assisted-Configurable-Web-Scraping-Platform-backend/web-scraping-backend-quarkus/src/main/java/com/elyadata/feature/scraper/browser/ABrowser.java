package com.elyadata.feature.scraper.browser;

import com.elyadata.feature.job.domain.model.PageAction;
import com.elyadata.feature.job.domain.model.SessionConfig;

public interface ABrowser extends AutoCloseable {

    void start();

    void close();

    void navigate(String url);

    PageSnapshot snapshot();

    void dismissCookies();

    void scroll(boolean toBottom);

    void waitForSelector(String selector);

    void click(String selector);

    default boolean performAction(PageAction action) {
        return true;
    }

    default void authenticate(SessionConfig sessionConfig) {
        // no-op by default
    }

    default String exportSessionState() {
        return null;
    }
}