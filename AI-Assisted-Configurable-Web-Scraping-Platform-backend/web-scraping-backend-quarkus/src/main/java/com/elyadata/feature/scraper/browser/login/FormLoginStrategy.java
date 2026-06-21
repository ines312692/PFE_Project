package com.elyadata.feature.scraper.browser.login;

import com.elyadata.feature.job.domain.model.SessionConfig;
import com.elyadata.feature.job.domain.model.SessionConfig.LoginMode;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class FormLoginStrategy implements LoginStrategy {

    private static final int NAV_TIMEOUT_MS = 30_000;
    private static final int FIELD_TIMEOUT_MS = 15_000;
    private static final int SUCCESS_TIMEOUT_MS = 20_000;

    @Override
    public LoginMode mode() {
        return LoginMode.FORM;
    }

    @Override
    public void login(BrowserContext context, SessionConfig cfg) {
        requireField(cfg.loginUrl(), "loginUrl");
        requireField(cfg.usernameSelector(), "usernameSelector");
        requireField(cfg.passwordSelector(), "passwordSelector");
        requireField(cfg.submitSelector(), "submitSelector");

        String username = cfg.usernameEnc();
        String password = cfg.passwordEnc();
        if (username == null || password == null) {
            throw new IllegalStateException("FORM login requires stored username/password");
        }

        Page page = context.newPage();
        try {
            log.info("[LOGIN][FORM] Navigating to login page {}", cfg.loginUrl());
            page.navigate(cfg.loginUrl(),
                    new Page.NavigateOptions().setTimeout(NAV_TIMEOUT_MS));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            page.waitForSelector(cfg.usernameSelector(),
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(FIELD_TIMEOUT_MS));

            page.fill(cfg.usernameSelector(), username);
            page.fill(cfg.passwordSelector(), password);
            log.info("[LOGIN][FORM] Credentials entered, submitting");
            page.click(cfg.submitSelector());

            verifySuccess(page, cfg);
            log.info("[LOGIN][FORM] Login succeeded");
        } finally {
            page.close();
        }
    }

    private void verifySuccess(Page page, SessionConfig cfg) {
        if (cfg.successSelector() != null && !cfg.successSelector().isBlank()) {
            page.waitForSelector(cfg.successSelector(),
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(SUCCESS_TIMEOUT_MS));
        } else {
            page.waitForLoadState(LoadState.NETWORKIDLE);
            log.debug("[LOGIN][FORM] No successSelector configured — relying on navigation only");
        }
    }

    private static void requireField(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("FORM login missing required field: " + name);
        }
    }
}