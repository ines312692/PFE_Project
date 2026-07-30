package com.elyadata.feature.scraper.browser.session;

import java.util.Optional;


public interface SessionStorePort {


    Optional<SessionState> find(String domain);

    void save(SessionState state);

    void invalidate(String domain);
}