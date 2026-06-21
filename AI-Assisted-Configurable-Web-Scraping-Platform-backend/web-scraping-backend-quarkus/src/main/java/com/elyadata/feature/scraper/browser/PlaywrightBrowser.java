package com.elyadata.feature.scraper.browser;

import com.elyadata.feature.job.domain.model.PageAction;
import com.elyadata.feature.job.domain.model.SessionConfig;
import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.scraper.browser.humanizer.Humanizer;
import com.elyadata.feature.scraper.browser.humanizer.HumanizerLevel;
import com.elyadata.feature.scraper.browser.llm.ActionSelectorResolver;
import com.elyadata.feature.scraper.browser.login.LoginStrategy;
import com.elyadata.feature.scraper.browser.login.LoginStrategyRegistry;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfile;
import com.elyadata.feature.scraper.browser.stealth.StealthProfile;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class PlaywrightBrowser implements ABrowser {

    private static final int COOKIE_TIMEOUT_MS = 3_000;
    private static final int SCROLL_RESET_DELAY_MS = 300;
    private static final int DELAY_BASE_MIN_MS = 500;
    private static final int POST_CLICK_DELAY_MS = 500;
    private static final int SELECTOR_TIMEOUT_MS = 15_000;
    private static final int POST_ACTION_SETTLE_MS = 400;
    private static final int TYPE_DELAY_MS = 120;

    private static final int SIDECAR_HEALTH_TIMEOUT_MS = 1_500;

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36";

    private static final List<String> COOKIE_SELECTORS = List.of(
            "#onetrust-accept-btn-handler",
            "#didomi-notice-agree-button",
            "button#accept-all",
            "[aria-label='Accept all cookies']"
    );

    private final InternalScrapeRequest request;
    private final StealthProfile stealthProfile;
    private final ConsistencyProfile consistencyProfile;
    private final Humanizer humanizer;
    private final HumanizerLevel humanizerLevel;
    private final LoginStrategyRegistry loginStrategyRegistry;
    private final ActionSelectorResolver actionSelectorResolver;


    private final String seedStorageState;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    public PlaywrightBrowser(InternalScrapeRequest request,
                             StealthProfile stealthProfile,
                             ConsistencyProfile consistencyProfile,
                             Humanizer humanizer,
                             HumanizerLevel humanizerLevel,
                             LoginStrategyRegistry loginStrategyRegistry,
                             ActionSelectorResolver actionSelectorResolver,
                             String seedStorageState) {
        this.request = request;
        this.stealthProfile = stealthProfile;
        this.consistencyProfile = consistencyProfile;
        this.humanizer = humanizer;
        this.humanizerLevel = humanizerLevel;
        this.loginStrategyRegistry = loginStrategyRegistry;
        this.actionSelectorResolver = actionSelectorResolver;
        this.seedStorageState = seedStorageState;
    }

    public PlaywrightBrowser(InternalScrapeRequest request,
                             StealthProfile stealthProfile,
                             ConsistencyProfile consistencyProfile,
                             Humanizer humanizer,
                             HumanizerLevel humanizerLevel) {
        this(request, stealthProfile, consistencyProfile, humanizer, humanizerLevel,
                null, null, null);
    }

    @Override
    public void start() {
        playwright = Playwright.create();
        boolean headless = ConfigProvider.getConfig()
                .getOptionalValue("scraper.browser.headless", Boolean.class)
                .orElse(true);

        log.info("[PLAYWRIGHT] Launching with headless={}", headless);

        BrowserType.LaunchOptions launchOpts = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(buildLaunchArgs());

        applyProxyIfPresent(launchOpts);

        browser = playwright.chromium().launch(launchOpts);

        Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions();
        if (request.isStealthMode()) {
            stealthProfile.prepareContextOptions(ctxOpts, consistencyProfile);
        } else {
            ctxOpts.setUserAgent(resolveLegacyUserAgent())
                    .setViewportSize(1366, 768)
                    .setLocale("fr-FR")
                    .setTimezoneId("Europe/Paris");
        }

        if (seedStorageState != null && !seedStorageState.isBlank()) {
            ctxOpts.setStorageState(seedStorageState);
            log.info("[PLAYWRIGHT] Context seeded with storageState ({} chars)",
                    seedStorageState.length());
        }

        context = browser.newContext(ctxOpts);

        if (request.isStealthMode()) {
            stealthProfile.applyInitScripts(context, consistencyProfile);
        }

        page = context.newPage();
        page.setDefaultTimeout(request.getTimeoutMs());

        humanizer.reset();

        log.info("[PLAYWRIGHT] Started — stealth={} humanizer={} timeout={}ms proxy={} seeded={}",
                request.isStealthMode(), humanizerLevel, request.getTimeoutMs(),
                request.getProxy() != null ? request.getProxy().endpoint() : "none",
                seedStorageState != null && !seedStorageState.isBlank());
    }

    @Override
    public void authenticate(SessionConfig sessionConfig) {
        if (sessionConfig == null || !sessionConfig.isUsable()) {
            return;
        }
        if (loginStrategyRegistry == null) {
            throw new IllegalStateException(
                    "Session login requested but this PlaywrightBrowser was built without a "
                            + "LoginStrategyRegistry (legacy constructor). Use the full constructor "
                            + "for authenticated scraping.");
        }
        try {
            LoginStrategy strategy = loginStrategyRegistry.get(sessionConfig.mode());
            log.info("[PLAYWRIGHT] Authenticating via mode={}", sessionConfig.mode());
            strategy.login(context, sessionConfig);
        } catch (Exception e) {
            log.error("[PLAYWRIGHT] Authentication failed (mode={}): {}",
                    sessionConfig.mode(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String exportSessionState() {
        if (context == null) {
            return null;
        }
        try {
            return context.storageState();
        } catch (Exception e) {
            log.warn("[PLAYWRIGHT] storageState export failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void navigate(String url) {
        applyRandomDelay();
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(request.getTimeoutMs()));
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(request.getTimeoutMs()));
            log.info("[PLAYWRIGHT] Navigated -> {}", url);
            humanizer.afterPageLoad(page, humanizerLevel);
        } catch (Exception e) {
            log.warn("[PLAYWRIGHT] NETWORKIDLE timeout on {}: {}", url, e.getMessage());
        }
    }

    @Override
    public PageSnapshot snapshot() {
        String html = page.content();
        String text = (String) page.evaluate("() => document.body?.innerText ?? ''");
        return PageSnapshot.builder()
                .url(page.url())
                .html(html)
                .text(text)
                .browserContext(LiveBrowserContext.builder().page(page).build())
                .build();
    }

    @Override
    public void dismissCookies() {
        for (String selector : COOKIE_SELECTORS) {
            try {
                Locator btn = page.locator(selector);
                btn.waitFor(new Locator.WaitForOptions()
                        .setTimeout(COOKIE_TIMEOUT_MS)
                        .setState(WaitForSelectorState.VISIBLE));
                humanizer.beforeClick(page, selector, humanizerLevel);
                btn.click();
                log.debug("[PLAYWRIGHT] Cookie dismissed via '{}'", selector);
                sleep(POST_CLICK_DELAY_MS);
                return;
            } catch (Exception ignored) {
                // Selector isn't found / not visible — try next candidate
            }
        }
    }

    @Override
    public void scroll(boolean toBottom) {
        humanizer.scroll(page, toBottom, humanizerLevel);
        try {
            page.evaluate("() => window.scrollTo(0, 0)");
        } catch (Exception e) {
            log.debug("[PLAYWRIGHT] scroll reset failed: {}", e.getMessage());
        }
        sleep(SCROLL_RESET_DELAY_MS);
    }

    @Override
    public void waitForSelector(String selector) {
        try {
            page.waitForSelector(selector,
                    new Page.WaitForSelectorOptions().setTimeout(SELECTOR_TIMEOUT_MS));
            log.debug("[PLAYWRIGHT] Selector ready: '{}'", selector);
        } catch (Exception e) {
            log.warn("[PLAYWRIGHT] Selector timeout: '{}'", selector);
        }
    }

    @Override
    public void click(String selector) {
        try {
            humanizer.beforeClick(page, selector, humanizerLevel);
            page.click(selector);
            sleep(POST_CLICK_DELAY_MS);
        } catch (Exception e) {
            log.warn("[PLAYWRIGHT] Click failed '{}': {}", selector, e.getMessage());
        }
    }


    @Override
    public boolean performAction(PageAction action) {
        if (action == null || !action.isValid()) {
            log.warn("[PLAYWRIGHT][ACTION] Skipping null/invalid action: {}",
                    action != null ? action.type() : "null");
            return action == null || action.isOptional();
        }

        try {
            switch (action.type()) {
                case CLICK -> doClick(action.selector());
                case FILL -> doFill(action.selector(), action.value());
                case TYPE -> doType(action.selector(), action.value());
                case SELECT -> page.selectOption(action.selector(), action.value());
                case PRESS_KEY -> doPressKey(action.selector(), action.value());
                case WAIT_FOR_SELECTOR -> doWaitForSelector(action.selector(), action.resolvedTimeoutMs());
                case WAIT_MS -> sleep(action.parseWaitMs());
                case SCROLL_TO_BOTTOM -> scroll(true);
                case HOVER -> page.hover(action.selector());
                case EVAL_JS -> page.evaluate(action.value());
                case LLM_CLICK -> doLlmClick(action);
            }
            sleep(POST_ACTION_SETTLE_MS);
            log.debug("[PLAYWRIGHT][ACTION] ok -> {}", action.describe());
            return true;

        } catch (Exception e) {
            String level = action.isOptional() ? "optional" : "REQUIRED";
            log.warn("[PLAYWRIGHT][ACTION] {} action failed ({}): {}",
                    level, action.describe(), e.getMessage());
            return action.isOptional();
        }
    }

    private void doPressKey(String selector, String key) {
        Locator el = page.locator(selector);
        el.focus();
        el.press(key);
    }
    private void doClick(String selector) {
        humanizer.beforeClick(page, selector, humanizerLevel);
        page.click(selector);
    }

    private void doFill(String selector, String value) {
        humanizer.beforeClick(page, selector, humanizerLevel);
        page.fill(selector, value);
    }


    private void doType(String selector, String value) {
        humanizer.beforeClick(page, selector, humanizerLevel);
        Locator field = page.locator(selector);
        field.click();
        field.fill("");
        field.pressSequentially(value,
                new Locator.PressSequentiallyOptions().setDelay(TYPE_DELAY_MS));
    }

    private void doWaitForSelector(String selector, int timeoutMs) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
    }

    private void doLlmClick(PageAction action) {
        if (actionSelectorResolver == null) {
            throw new IllegalStateException(
                    "LLM_CLICK requested but this PlaywrightBrowser was built without an "
                            + "ActionSelectorResolver (legacy constructor). Use the full constructor "
                            + "for AI-assisted actions.");
        }

        String containerHint = action.selector();
        if (containerHint != null && !containerHint.isBlank()) {
            page.waitForSelector(containerHint, new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(action.resolvedTimeoutMs()));
        }

        // Give lazy-loaded widgets (Auchan journey-locator) a chance to appear.
        // This mirrors the planning-time render that DOES see the element.
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(5_000));
        } catch (Exception ignored) {
            // best-effort — page may already be idle
        }
        humanizer.scroll(page, true, humanizerLevel);   // scroll down to trigger lazy-load
        sleep(1_000);
        try {
            page.evaluate("() => window.scrollTo(0, 0)");
        } catch (Exception ignored) {
            // best-effort
        }
        sleep(500);

        try {
            Object diag = page.evaluate("""
        () => {
          const out = [];
          document.querySelectorAll('iframe').forEach((f, i) => {
            out.push(`#${i} id="${f.id}" name="${f.name}" cls="${f.className}" src="${(f.src||'').slice(0,80)}"`);
          });
          return out.join(' || ');
        }
    """);
            log.warn("[LLM-CLICK][IFRAMES] {}", diag);

            // Cherche le bouton dans chaque frame Playwright
            for (Frame f : page.frames()) {
                try {
                    int n = f.locator("text=Afficher le prix").count();
                    if (n > 0) {
                        log.warn("[LLM-CLICK][FOUND] frame name='{}' url='{}' matches={}",
                                f.name(), f.url(), n);
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            log.warn("[LLM-CLICK][IFRAMES] failed: {}", e.getMessage());
        }

        String html = page.content();
        log.info("[PLAYWRIGHT][ACTION][LLM] Captured {} chars for resolution of '{}'",
                html.length(), action.value());

        Optional<String> resolved =
                actionSelectorResolver.resolveClickTarget(html, action.value(), containerHint);

        if (resolved.isEmpty()) {
            throw new IllegalStateException(
                    "LLM could not resolve a click target for: \"" + action.value() + "\""
                            + " (the element may not be in the DOM — check the interaction)");
        }

        String selector = resolved.get();
        log.info("[PLAYWRIGHT][ACTION][LLM] Resolved click target -> '{}'", selector);
        humanizer.beforeClick(page, selector, humanizerLevel);
        page.locator(selector).first().click();
    }

    @Override
    public void close() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception ignored) {
                // Best-effort close
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
                // Best-effort close
            }
        }
        log.info("[PLAYWRIGHT] Browser closed");
    }

    private List<String> buildLaunchArgs() {
        List<String> args = new ArrayList<>(List.of(
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--disable-gpu",
                "--disable-extensions",
                "--disable-blink-features=AutomationControlled",
                "--disable-features=IsolateOrigins,site-per-process",
                "--force-webrtc-ip-handling-policy=default_public_interface_only",
                "--disable-features=WebRtcHideLocalIpsWithMdns"
        ));
        if ("true".equalsIgnoreCase(System.getenv("TLS_SIDECAR_ENABLED"))) {
            args.add("--ignore-certificate-errors");
        }

        if (request.isStealthMode()) {
            args.add("--disable-infobars");
        }
        return args;
    }

    private void applyProxyIfPresent(BrowserType.LaunchOptions launchOpts) {
        final Proxy proxy = request.getProxy();

        if (tryApplySidecar(launchOpts, proxy)) {
            return;
        }

        if (proxy == null) {
            log.info("[PLAYWRIGHT] Connexion directe (ni sidecar ni proxy)");
            return;
        }

        applyDirectProxy(launchOpts, proxy);
    }

    private boolean tryApplySidecar(BrowserType.LaunchOptions launchOpts,
                                    Proxy proxy) {
        if (!"true".equalsIgnoreCase(System.getenv("TLS_SIDECAR_ENABLED"))) {
            return false;
        }

        String sidecarUrl = System.getenv()
                .getOrDefault("TLS_SIDECAR_URL", "http://localhost:8888");

        if (!sidecarReachable(sidecarUrl)) {
            log.error("[PLAYWRIGHT] Sidecar injoignable ({}) — bascule rotation/direct "
                    + "(furtivite TLS desactivee pour ce job)", sidecarUrl);
            return false;
        }

        try {
            launchOpts.setProxy(new com.microsoft.playwright.options.Proxy(sidecarUrl));
            if (proxy != null) {
                log.warn("[PLAYWRIGHT] Sidecar actif -> proxy '{}' du pool IGNORE "
                                + "(configurer UPSTREAM_PROXY cote sidecar si sortie via proxy voulue)",
                        proxy.endpoint());
            }
            log.info("[PLAYWRIGHT] Mode FURTIF -> {} (TLS Chrome 120)", sidecarUrl);
            return true;
        } catch (Exception e) {
            log.error("[PLAYWRIGHT] URL sidecar invalide '{}' ({}) — bascule rotation/direct",
                    sidecarUrl, e.getMessage());
            return false;
        }
    }

    private void applyDirectProxy(BrowserType.LaunchOptions launchOpts,
                                  Proxy proxy) {
        try {
            com.microsoft.playwright.options.Proxy pwProxy =
                    new com.microsoft.playwright.options.Proxy(proxy.url());
            if (proxy.hasCredentials()) {
                pwProxy.setUsername(proxy.username());
                pwProxy.setPassword(proxy.password() == null ? "" : proxy.password());
            }
            launchOpts.setProxy(pwProxy);
            log.info("[PLAYWRIGHT] Mode ROTATION -> proxy {} ({}{})",
                    proxy.endpoint(), proxy.protocol(),
                    proxy.hasCredentials() ? " auth" : "");
        } catch (Exception e) {
            log.error("[PLAYWRIGHT] Proxy invalide '{}' ({}) — bascule connexion directe",
                    proxy.endpoint(), e.getMessage());
        }
    }

    private boolean sidecarReachable(String sidecarUrl) {
        try {
            java.net.URI base = java.net.URI.create(sidecarUrl);
            String healthUrl = base.getScheme() + "://" + base.getAuthority() + "/health";

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(SIDECAR_HEALTH_TIMEOUT_MS))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(healthUrl))
                    .timeout(java.time.Duration.ofMillis(SIDECAR_HEALTH_TIMEOUT_MS))
                    .GET().build();

            int status = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != 200) {
                log.warn("[PLAYWRIGHT] Sidecar /health -> HTTP {}", status);
            }
            return status == 200;
        } catch (Exception e) {
            log.warn("[PLAYWRIGHT] Sidecar health-check echoue: {}", e.getMessage());
            return false;
        }
    }

    private String resolveLegacyUserAgent() {
        if (request.getUserAgent() != null && !request.getUserAgent().isBlank()) {
            return request.getUserAgent();
        }
        return DEFAULT_USER_AGENT;
    }

    private void applyRandomDelay() {
        int margin = request.getDelayMs();
        long actual = (long) DELAY_BASE_MIN_MS
                + ThreadLocalRandom.current().nextInt(0, Math.max(1, margin + 1));
        log.debug("[PLAYWRIGHT] Request delay: {}ms (base={}ms margin={}ms)",
                actual, DELAY_BASE_MIN_MS, margin);
        sleep(actual);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}