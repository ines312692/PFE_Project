package com.elyadata.feature.scraper.browser.pool;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Slf4j
public class BrowserSlot {

    private static final int CLOSE_TIMEOUT_SEC = 20;
    private static final int LAUNCH_TIMEOUT_SEC = 90;

    @Getter
    private final int index;

    private final ExecutorService ownerThread;
    private final boolean headless;
    private final int maxContextReuse;

    private Playwright playwright;
    private Browser browser;
    private String launchedProxyKey;

    private int contextsServed;

    public BrowserSlot(int index, boolean headless, int maxContextReuse) {
        this.index = index;
        this.headless = headless;
        this.maxContextReuse = maxContextReuse;
        this.ownerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "browser-slot-" + index);
            t.setDaemon(true);
            return t;
        });
    }

    public BrowserContext newContext(Proxy proxy,
                                     boolean sidecarMode,
                                     String sidecarUrl,
                                     Browser.NewContextOptions ctxOptions) {
        return onOwnerThread(() -> {
            ensureBrowser(proxy, sidecarMode, sidecarUrl);
            BrowserContext ctx = browser.newContext(ctxOptions);
            contextsServed++;
            log.debug("[POOL][slot-{}] context créé ({}/{} avant recyclage) sortie={}",
                    index, contextsServed, maxContextReuse, launchedProxyKey);
            return ctx;
        });
    }

    public void releaseContext(BrowserContext ctx, boolean forceRecycle) {
        onOwnerThread(() -> {
            closeQuietly(ctx);
            if (forceRecycle || contextsServed >= maxContextReuse) {
                log.info("[POOL][slot-{}] recyclage du Chrome (servis={} force={})",
                        index, contextsServed, forceRecycle);
                disposeBrowser();
            }
            return null;
        });
    }

    private void ensureBrowser(Proxy proxy, boolean sidecarMode, String sidecarUrl) {
        String wantedKey = resolveProxyKey(proxy, sidecarMode, sidecarUrl);

        if (browser != null && browser.isConnected()) {
            if (Objects.equals(wantedKey, launchedProxyKey)) {
                return;
            }
            log.info("[POOL][slot-{}] sortie réseau change ({} -> {}) — relance du Chrome",
                    index, launchedProxyKey, wantedKey);
            disposeBrowser();
        }

        launch(proxy, sidecarMode, sidecarUrl, wantedKey);
    }

    private void launch(Proxy proxy, boolean sidecarMode, String sidecarUrl, String proxyKey) {
        if (playwright == null) {
            playwright = Playwright.create();
            log.info("[POOL][slot-{}] Playwright driver démarré", index);
        }

        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(launchArgs(sidecarMode));

        applyLaunchProxy(opts, proxy, sidecarMode, sidecarUrl);

        browser = playwright.chromium().launch(opts);
        launchedProxyKey = proxyKey;
        contextsServed = 0;

        log.info("[POOL][slot-{}] Chrome lancé (headless={} sortie={})",
                index, headless, proxyKey);
    }

    private void disposeBrowser() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.debug("[POOL][slot-{}] close browser: {}", index, e.getMessage());
            }
            browser = null;
        }
        launchedProxyKey = null;
        contextsServed = 0;
    }

    public void shutdown() {
        try {
            onOwnerThread(() -> {
                disposeBrowser();
                if (playwright != null) {
                    try {
                        playwright.close();
                    } catch (Exception ignored) {
                        // best-effort
                    }
                    playwright = null;
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[POOL][slot-{}] shutdown partiel: {}", index, e.getMessage());
        }
        ownerThread.shutdown();
        try {
            if (!ownerThread.awaitTermination(CLOSE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                ownerThread.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ownerThread.shutdownNow();
        }
    }

    private static void applyLaunchProxy(BrowserType.LaunchOptions opts,
                                         Proxy proxy,
                                         boolean sidecarMode,
                                         String sidecarUrl) {
        if (sidecarMode) {
            opts.setProxy(new com.microsoft.playwright.options.Proxy(sidecarUrl));
            return;
        }
        if (proxy == null) {
            return;
        }
        try {
            com.microsoft.playwright.options.Proxy pw =
                    new com.microsoft.playwright.options.Proxy(proxy.url());
            if (proxy.hasCredentials()) {
                pw.setUsername(proxy.username());
                pw.setPassword(proxy.password() == null ? "" : proxy.password());
            }
            opts.setProxy(pw);
        } catch (Exception e) {
            log.error("[POOL] proxy invalide '{}' ({}) — Chrome en direct",
                    proxy.endpoint(), e.getMessage());
        }
    }

    private static String resolveProxyKey(Proxy proxy, boolean sidecarMode, String sidecarUrl) {
        if (sidecarMode) {
            return "sidecar:" + sidecarUrl;
        }
        if (proxy == null) {
            return "direct";
        }
        return "proxy:" + proxy.endpoint();
    }

    private static List<String> launchArgs(boolean sidecarMode) {
        List<String> args = new ArrayList<>(List.of(
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--disable-gpu",
                "--disable-extensions",
                "--disable-infobars",
                "--disable-blink-features=AutomationControlled",
                "--disable-features=IsolateOrigins,site-per-process",
                "--force-webrtc-ip-handling-policy=default_public_interface_only",
                "--disable-features=WebRtcHideLocalIpsWithMdns"
        ));
        if (sidecarMode) {
            args.add("--ignore-certificate-errors");
        }
        return args;
    }

    private static void closeQuietly(BrowserContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            ctx.close();
        } catch (Exception e) {
            log.debug("[POOL] close context: {}", e.getMessage());
        }
    }

    private <T> T onOwnerThread(Callable<T> action) {
        try {
            return ownerThread.submit(action).get(LAUNCH_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Slot " + index + " interrompu", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Slot " + index + " a échoué", cause);
        } catch (Exception e) {
            throw new IllegalStateException("Slot " + index + " timeout", e);
        }
    }
}