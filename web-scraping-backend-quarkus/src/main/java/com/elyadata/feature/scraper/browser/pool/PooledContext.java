package com.elyadata.feature.scraper.browser.pool;

import com.microsoft.playwright.BrowserContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor
public class PooledContext implements AutoCloseable {

    private final BrowserContext context;
    private final BrowserSlot slot;
    private final BrowserPool pool;
    private final boolean exclusive;

    private boolean closed;
    private boolean poisoned;

    public void poison() {
        this.poisoned = true;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pool.release(this);
    }
}