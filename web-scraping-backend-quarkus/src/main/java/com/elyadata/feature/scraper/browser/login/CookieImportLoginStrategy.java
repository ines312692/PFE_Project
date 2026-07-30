package com.elyadata.feature.scraper.browser.login;

import com.elyadata.feature.job.domain.model.SessionConfig;
import com.elyadata.feature.job.domain.model.SessionConfig.LoginMode;
import com.microsoft.playwright.BrowserContext;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@ApplicationScoped
public class CookieImportLoginStrategy implements LoginStrategy {

    @Override
    public LoginMode mode() {
        return LoginMode.COOKIE_IMPORT;
    }

    @Override
    public void login(BrowserContext context, SessionConfig cfg) {
        if (cfg.importedStorageState() == null || cfg.importedStorageState().isBlank()) {
            throw new IllegalStateException("COOKIE_IMPORT login requires importedStorageState JSON");
        }
        log.info("[LOGIN][COOKIE_IMPORT] storageState present ({} chars) — applied at context creation",
                cfg.importedStorageState().length());
    }
}