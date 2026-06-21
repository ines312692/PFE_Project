package com.elyadata.feature.scraper.browser;

import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class SeleniumBrowser implements ABrowser {

    private static final int CLOUDFLARE_INITIAL_WAIT_MS = 15_000;
    private static final int CLOUDFLARE_RETRY_WAIT_MS = 15_000;
    private static final int COOKIE_CLICK_WAIT_MS = 1_000;
    private static final int SCROLL_STEP_DELAY_MS = 600;
    private static final int SCROLL_PARTIAL_DELAY_MS = 500;
    private static final int SCROLL_RESET_DELAY_MS = 300;
    private static final int SELECTOR_TIMEOUT_SEC = 15;
    private static final int COOKIE_TIMEOUT_SEC = 3;
    private static final int MIN_REAL_PAGE_HTML_LENGTH = 5_000;
    private static final int DELAY_BASE_MIN_MS = 500;

    private static final Set<String> CLOUDFLARE_TITLES = Set.of(
            "robot", "challenge", "attention required", "just a moment"
    );

    private static final List<String> COOKIE_SELECTORS = List.of(
            "#onetrust-accept-btn-handler",
            "#didomi-notice-agree-button",
            "button#accept-all"
    );

    private static final List<String> CHROME_BINARY_CANDIDATES = List.of(
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/google-chrome-beta",
            "/usr/bin/chromium-browser",
            "/usr/bin/chromium",
            "/snap/bin/chromium",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium"
    );

    private final InternalScrapeRequest request;
    private WebDriver driver;
    private boolean cloudflarePassed = false;

    public SeleniumBrowser(InternalScrapeRequest request) {
        this.request = request;
    }

    @Override
    public void start() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions opts = new ChromeOptions();
        configureBinary(opts);
        configureChromeArgs(opts);

        driver = new ChromeDriver(opts);
        int timeoutSec = Math.max(90, request.getTimeoutMs() / 1000);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeoutSec));

        log.info("[SELENIUM] Browser started — timeout={}s stealthMode={} customUA={}",
                timeoutSec, request.isStealthMode(), request.getUserAgent() != null);
    }

    @Override
    public void close() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.debug("[SELENIUM] Ignore error on close: {}", e.getMessage());
            }
        }
        log.info("[SELENIUM] Browser closed");
    }

    @Override
    public void navigate(String url) {
        ensureSessionAlive();
        applyRandomDelay();

        long startMs = System.currentTimeMillis();
        driver.get(url);

        if (cloudflarePassed && !isCurrentlyBlocked()) {
            log.info("[SELENIUM] Navigated → {} — fast path ({}ms)", url, System.currentTimeMillis() - startMs);
            return;
        }

        if (!isCurrentlyBlocked()) {
            cloudflarePassed = true;
            log.info("[SELENIUM] Navigated → {} — page ready ({}ms)", url, System.currentTimeMillis() - startMs);
            return;
        }

        waitForCloudflare(url);
        cloudflarePassed = true;
        log.info("[SELENIUM] Navigated → {} — Cloudflare cleared ({}ms)", url, System.currentTimeMillis() - startMs);
    }

    @Override
    public PageSnapshot snapshot() {
        String html = driver.getPageSource();
        String text = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.body?.innerText || ''");
        return PageSnapshot.builder()
                .url(driver.getCurrentUrl())
                .html(html)
                .text(text)
                .browserContext(LiveBrowserContext.builder().driver(driver).build())
                .build();
    }

    @Override
    public void dismissCookies() {
        for (String sel : COOKIE_SELECTORS) {
            try {
                var btn = new WebDriverWait(driver, Duration.ofSeconds(COOKIE_TIMEOUT_SEC))
                        .until(ExpectedConditions.elementToBeClickable(By.cssSelector(sel)));
                btn.click();
                sleep(COOKIE_CLICK_WAIT_MS);
                log.debug("[SELENIUM] Cookie dismissed via '{}'", sel);
                return;
            } catch (Exception ignored) {
                // Best-effort close; nothing to recover from
            }
        }
    }

    @Override
    public void scroll(boolean toBottom) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        if (toBottom) {
            scrollToBottom(js);
        } else {
            scrollPartial(js);
        }
        js.executeScript("window.scrollTo(0, 0)");
        sleep(SCROLL_RESET_DELAY_MS);
    }

    @Override
    public void waitForSelector(String selector) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(SELECTOR_TIMEOUT_SEC))
                    .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
            log.debug("[SELENIUM] Selector found: '{}'", selector);
        } catch (Exception e) {
            log.warn("[SELENIUM] Selector timeout: '{}'", selector);
        }
    }

    @Override
    public void click(String selector) {
        try {
            driver.findElement(By.cssSelector(selector)).click();
            sleep(COOKIE_CLICK_WAIT_MS);
        } catch (Exception e) {
            log.warn("[SELENIUM] Click failed '{}': {}", selector, e.getMessage());
        }
    }

    private void applyRandomDelay() {
        int margin = request.getDelayMs();
        long actual = (long) DELAY_BASE_MIN_MS + ThreadLocalRandom.current().nextInt(0, Math.max(1, margin + 1));
        log.debug("[SELENIUM] Request delay: {}ms (base={}ms margin={}ms)", actual, DELAY_BASE_MIN_MS, margin);
        sleep(actual);
    }

    private void ensureSessionAlive() {
        if (driver == null) {
            log.warn("[SELENIUM] driver=null — restarting");
            start();
            return;
        }
        try {
            driver.getTitle();
        } catch (Exception e) {
            if (isSessionDeadError(e)) {
                log.warn("[SELENIUM] Session invalide détectée — restart du browser: {}", e.getMessage());
                restartBrowser();
            }
        }
    }

    private void restartBrowser() {
        try {
            driver.quit();
        } catch (Exception ignored) {
        }
        cloudflarePassed = false;
        log.info("[SELENIUM] Restarting browser after session crash...");
        start();
        log.info("[SELENIUM] Browser restarted successfully");
    }

    private static boolean isSessionDeadError(Exception e) {
        if (e == null || e.getMessage() == null) return false;
        String msg = e.getMessage().toLowerCase();
        return msg.contains("invalid session id")
                || msg.contains("no such session")
                || msg.contains("session deleted")
                || msg.contains("unable to connect");
    }


    private void configureBinary(ChromeOptions opts) {
        String binary = findChromeBinary();
        log.info("[SELENIUM] Using Chrome binary: {}", binary);
        opts.setBinary(binary);
    }

    private void configureChromeArgs(ChromeOptions opts) {
        opts.addArguments(
                "--lang=fr-FR",
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--window-size=1366,768",
                "--disable-gpu",
                "--disable-extensions",
                "--disable-blink-features=AutomationControlled"
        );
        opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        opts.setExperimentalOption("useAutomationExtension", false);

        if (request.getUserAgent() != null && !request.getUserAgent().isBlank()) {
            opts.addArguments("--user-agent=" + request.getUserAgent());
            log.info("[SELENIUM] Custom User-Agent applied: {}", request.getUserAgent());
        }

        applyProxyIfPresent(opts);
    }

    private void applyProxyIfPresent(ChromeOptions opts) {
        com.elyadata.feature.proxy.domain.model.Proxy proxy = request.getProxy();
        if (proxy == null) return;

        if (proxy.hasCredentials()) {
            log.warn("[SELENIUM] Proxy {} has credentials but Selenium does not support auth proxies " +
                            "natively — credentials will be ignored. Use Playwright for authenticated proxies.",
                    proxy.endpoint());
        }

        String endpoint = proxy.endpoint();
        Proxy seleniumProxy = getSeleniumProxy(proxy, endpoint);

        opts.setCapability("proxy", seleniumProxy);
        log.info("[SELENIUM] Proxy applied → {} ({})", endpoint, proxy.protocol());
    }

    private static @NonNull Proxy getSeleniumProxy(com.elyadata.feature.proxy.domain.model.Proxy proxy, String endpoint) {
        Proxy seleniumProxy = new Proxy();

        switch (proxy.protocol()) {
            case HTTP, HTTPS -> {
                seleniumProxy.setHttpProxy(endpoint);
                seleniumProxy.setSslProxy(endpoint);
            }
            case SOCKS4 -> {
                seleniumProxy.setSocksProxy(endpoint);
                seleniumProxy.setSocksVersion(4);
            }
            case SOCKS5 -> {
                seleniumProxy.setSocksProxy(endpoint);
                seleniumProxy.setSocksVersion(5);
            }
        }
        return seleniumProxy;
    }

    private static String findChromeBinary() {
        return CHROME_BINARY_CANDIDATES.stream()
                .filter(path -> new File(path).exists())
                .findFirst()
                .orElse(null);
    }

    private boolean isCurrentlyBlocked() {
        return isBlocked(driver.getTitle(), driver.getPageSource().length());
    }

    private void waitForCloudflare(String url) {
        log.info("[SELENIUM] Cloudflare detected on {} — waiting {}ms", url, CLOUDFLARE_INITIAL_WAIT_MS);
        sleep(CLOUDFLARE_INITIAL_WAIT_MS);

        if (!isCurrentlyBlocked()) return;

        log.warn("[SELENIUM] Still blocked — retrying after {}ms", CLOUDFLARE_RETRY_WAIT_MS);
        sleep(CLOUDFLARE_RETRY_WAIT_MS);

        if (isCurrentlyBlocked()) {
            cloudflarePassed = false;
            throw new RuntimeException("Cloudflare challenge not resolved for " + url);
        }
    }

    private static void scrollToBottom(JavascriptExecutor js) {
        long prev = 0;
        for (int i = 0; i < 30; i++) {
            js.executeScript("window.scrollBy(0, window.innerHeight)");
            sleep(SCROLL_STEP_DELAY_MS);
            long curr = (long) js.executeScript("return document.body.scrollHeight");
            if (curr == prev) break;
            prev = curr;
        }
    }

    private static void scrollPartial(JavascriptExecutor js) {
        for (int i = 0; i < 4; i++) {
            js.executeScript("window.scrollBy(0, window.innerHeight)");
            sleep(SCROLL_PARTIAL_DELAY_MS);
        }
    }

    private static boolean isBlocked(String title, int htmlLen) {
        if (title == null) return true;
        String lower = title.toLowerCase();
        return htmlLen < MIN_REAL_PAGE_HTML_LENGTH
                || CLOUDFLARE_TITLES.stream().anyMatch(lower::contains);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}