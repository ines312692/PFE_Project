package com.elyadata.feature.scraper.antibot.captcha.solvers.turnstile;

import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class TurnstileObserver {

    private static final String INSTALL_JS = """
            (argsJson) => {
              const args = JSON.parse(argsJson);
              const cfType = args.cfType;
              const solveTimeoutMs = args.solveTimeoutMs;

              if (window.__tsObsInstalled) return;
              window.__tsObsInstalled = true;
              window.__tsVerified = false;
              const start = Date.now();

              const watchInput = (input) => {
                if (input.value && input.value.length > 20) { window.__tsVerified = true; return; }
                const obs = new MutationObserver(() => {
                  if (input.value && input.value.length > 20) {
                    window.__tsVerified = true;
                    obs.disconnect();
                  }
                });
                obs.observe(input, { attributes: true, attributeFilter: ['value'] });
                setTimeout(() => obs.disconnect(), solveTimeoutMs);
              };

              const watchApi = () => {
                const iv = setInterval(() => {
                  try {
                    if (window.turnstile && typeof window.turnstile.getResponse === 'function') {
                      const t = window.turnstile.getResponse();
                      if (t && t.length > 20) { window.__tsVerified = true; clearInterval(iv); }
                    }
                  } catch(e) {}
                  if (Date.now() - start > solveTimeoutMs) clearInterval(iv);
                }, 500);
              };

              const findEmbedded = () => {
                const widget = document.querySelector(
                  '.cf-turnstile[data-sitekey], div[data-sitekey][class*="turnstile"]'
                );
                if (!widget) {
                  if (Date.now() - start < 10000) setTimeout(findEmbedded, 400);
                  return;
                }
                const input = widget.querySelector('input[type="hidden"][name*="turnstile" i]')
                           || widget.querySelector('input[type="hidden"]');
                if (input) watchInput(input);
                watchApi();
              };

              const findChallenge = () => {
                const ok = document.querySelector('#challenge-success-text');
                if (!ok || !ok.parentElement) {
                  if (Date.now() - start < 10000) setTimeout(findChallenge, 400);
                  return;
                }
                const check = () => {
                  if (ok.getClientRects().length > 0) window.__tsVerified = true;
                };
                check();
                const obs = new MutationObserver(check);
                obs.observe(ok.parentElement, { childList: true, attributes: true, subtree: true });
                setTimeout(() => obs.disconnect(), solveTimeoutMs);
                watchApi();
              };

              if (cfType === 'embedded')  findEmbedded();
              if (cfType === 'challenge') findChallenge();
            }
            """;

    private static final String CHECK_JS = "() => !!window.__tsVerified";

    public void install(Page page, String cfType, long solveTimeoutMs) {
        try {
            String argsJson = String.format(
                    "{\"cfType\":\"%s\",\"solveTimeoutMs\":%d}",
                    cfType, solveTimeoutMs);
            page.evaluate(INSTALL_JS, argsJson);
            log.debug("[TURNSTILE-OBS] installed cfType={} timeoutMs={}", cfType, solveTimeoutMs);
        } catch (Exception e) {
            log.warn("[TURNSTILE-OBS] install failed: {}", e.getMessage());
        }
    }

    public boolean isVerified(Page page) {
        try {
            Object r = page.evaluate(CHECK_JS);
            return r instanceof Boolean b && b;
        } catch (Exception e) {
            return false;
        }
    }
}