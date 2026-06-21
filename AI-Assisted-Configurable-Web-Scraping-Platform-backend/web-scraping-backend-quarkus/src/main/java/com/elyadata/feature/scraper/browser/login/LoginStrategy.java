package com.elyadata.feature.scraper.browser.login;

import com.elyadata.feature.job.domain.model.SessionConfig;
import com.elyadata.feature.job.domain.model.SessionConfig.LoginMode;
import com.microsoft.playwright.BrowserContext;


public interface LoginStrategy {

    LoginMode mode();

    void login(BrowserContext context, SessionConfig config);
}