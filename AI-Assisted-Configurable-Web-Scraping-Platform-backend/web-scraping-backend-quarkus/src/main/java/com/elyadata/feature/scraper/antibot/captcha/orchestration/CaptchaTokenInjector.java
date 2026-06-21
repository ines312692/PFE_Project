package com.elyadata.feature.scraper.antibot.captcha.orchestration;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class CaptchaTokenInjector {

    private static final String RECAPTCHA_V2_INJECT = """
            (token) => {
              try {
                document.querySelectorAll('textarea[name="g-recaptcha-response"], #g-recaptcha-response')
                  .forEach(el => { el.innerHTML = token; el.value = token; el.style.display = 'block'; });
            
                if (typeof ___grecaptcha_cfg !== 'undefined' && ___grecaptcha_cfg.clients) {
                  const clients = ___grecaptcha_cfg.clients;
                  Object.values(clients).forEach(c => {
                    Object.values(c).forEach(o => {
                      if (o && typeof o === 'object') {
                        Object.values(o).forEach(v => {
                          if (v && typeof v.callback === 'function') {
                            try { v.callback(token); } catch (_) {}
                          }
                        });
                      }
                    });
                  });
                }
                return true;
              } catch (e) { return false; }
            }
            """;

    private static final String RECAPTCHA_V3_INJECT = """
            (token) => {
              try {
                // 1) Patch grecaptcha.execute pour retourner notre token
                window.grecaptcha = window.grecaptcha || {};
                window.grecaptcha.ready = function(cb) {
                  try { cb && cb(); } catch (e) {}
                };
                window.grecaptcha.execute = function() {
                  return Promise.resolve(token);
                };
                window.grecaptcha.getResponse = function() { return token; };
            
                // 2) Remplit le textarea caché si présent (explicit render)
                const ta = document.querySelector(
                  'textarea[name="g-recaptcha-response"], #g-recaptcha-response'
                );
                if (ta) ta.value = token;
            
                // 3) Champs avec suffixe action (sites custom multi-action)
                document.querySelectorAll(
                  'textarea[name^="g-recaptcha-response-"], input[name^="g-recaptcha-response-"]'
                ).forEach(el => el.value = token);
            
                return true;
              } catch (e) {
                return false;
              }
            }
            """;

    private static final String HCAPTCHA_INJECT = """
            (token) => {
              try {
                document.querySelectorAll('textarea[name="h-captcha-response"], [name="h-captcha-response"]')
                  .forEach(el => { el.value = token; el.innerHTML = token; });
                document.querySelectorAll('textarea[name="g-recaptcha-response"]')
                  .forEach(el => { el.value = token; el.innerHTML = token; });
            
                document.querySelectorAll('.h-captcha, [data-hcaptcha-widget-id]').forEach(w => {
                  const cb = w.getAttribute('data-callback');
                  if (cb && typeof window[cb] === 'function') {
                    try { window[cb](token); } catch (_) {}
                  }
                });
                return true;
              } catch (e) { return false; }
            }
            """;

    private static final String TURNSTILE_INJECT = """
            (token) => {
              try {
                document.querySelectorAll('input[name="cf-turnstile-response"], textarea[name="cf-turnstile-response"]')
                  .forEach(el => { el.value = token; });
            
                document.querySelectorAll('.cf-turnstile, [data-sitekey]').forEach(w => {
                  const cb = w.getAttribute('data-callback');
                  if (cb && typeof window[cb] === 'function') {
                    try { window[cb](token); } catch (_) {}
                  }
                });
                return true;
              } catch (e) { return false; }
            }
            """;

    public boolean inject(PageSnapshot snapshot, CaptchaType type, String token) {
        Page page = extractPage(snapshot);
        if (page == null) {
            log.warn("[CAPTCHA-INJECT] No Playwright page available — cannot inject type={}", type);
            return false;
        }

        String script = scriptFor(type);
        if (script == null) {
            log.warn("[CAPTCHA-INJECT] No injection script available for type={} — skipping", type);
            return false;
        }

        try {
            Object result = page.evaluate(script, token);
            boolean ok = Boolean.TRUE.equals(result);
            if (ok) {
                log.info("[CAPTCHA-INJECT] Token injected for type={} (token={}...)",
                        type, token.substring(0, Math.min(12, token.length())));
            } else {
                log.warn("[CAPTCHA-INJECT] Injection script returned false for type={}", type);
            }
            return ok;
        } catch (Exception e) {
            log.warn("[CAPTCHA-INJECT] Failed to inject type={}: {}", type, e.getMessage());
            return false;
        }
    }

    private static String scriptFor(CaptchaType type) {
        return switch (type) {
            case RECAPTCHA_V2 -> RECAPTCHA_V2_INJECT;
            case RECAPTCHA_V3 -> RECAPTCHA_V3_INJECT;
            case HCAPTCHA -> HCAPTCHA_INJECT;
            case TURNSTILE -> TURNSTILE_INJECT;
            case IMAGE_TEXT, GEETEST, FUNCAPTCHA, CLOUDFLARE_INTERSTITIAL, CUSTOM, UNKNOWN, ROTATE -> null;
        };
    }

    private static Page extractPage(PageSnapshot snapshot) {
        if (snapshot == null) return null;
        LiveBrowserContext ctx = snapshot.getBrowserContext();
        return ctx != null && ctx.isPlaywright() ? ctx.getPage() : null;
    }

    public boolean injectTextAnswer(PageSnapshot snapshot, String inputSelector, String answer) {
        Page page = extractPage(snapshot);
        if (page == null || inputSelector == null) {
            log.warn("[CAPTCHA-INJECT] No page or selector — cannot inject text answer");
            return false;
        }
        try {
            page.fill(inputSelector, answer);
            log.info("[CAPTCHA-INJECT] Text answer typed into '{}' (len={})",
                    inputSelector, answer.length());
            return true;
        } catch (Exception e) {
            log.warn("[CAPTCHA-INJECT] Failed to type into '{}': {}", inputSelector, e.getMessage());
            return false;
        }
    }
}