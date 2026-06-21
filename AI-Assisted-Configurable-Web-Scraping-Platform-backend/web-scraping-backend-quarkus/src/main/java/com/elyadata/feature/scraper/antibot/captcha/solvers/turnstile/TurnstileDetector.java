package com.elyadata.feature.scraper.antibot.captcha.solvers.turnstile;

import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class TurnstileDetector {

    public enum TurnstileType {EMBEDDED, CHALLENGE, NONE}

    private static final String DETECT_JS = """
        () => {
          // 1) Embedded widget standalone (cas le plus simple, sites tiers)
          const widget = document.querySelector(
            '.cf-turnstile[data-sitekey], ' +
            'div[data-sitekey][class*="turnstile"], ' +
            '#turnstile-wrapper, ' +
            '[id^="cf-chl-widget-"]'
          );
          if (widget) {
            widget.scrollIntoView({ block: 'center', behavior: 'instant' });
            return 'embedded';
          }

          // 2) Iframe Cloudflare présent → c'est soit embedded soit challenge
          const iframe = document.querySelector(
            'iframe[src*="challenges.cloudflare.com"]'
          );

          // 3) Marqueurs d'interstitiel (DOM ou texte, FR + EN)
          const challengeDom = document.querySelector(
            '#challenge-stage, #challenge-form, #challenge-running, ' +
            '#challenge-success-text, .footer-inner, .core-msg, ' +
            '.zone, .main-wrapper'
          );

          const text = (document.body && document.body.innerText || '').toLowerCase();
          const interstitialPhrases = [
            "vérifiez que vous êtes humain",
            "verifiez que vous etes humain",
            "vérifions ensemble",
            "c'est une formalité",
            "verify you are human",
            "checking if the site connection is secure",
            "checking your browser",
            "needs to review the security of your connection",
            "un instant"
          ];
          const hasInterstitialText = interstitialPhrases.some(p => text.includes(p));
          const hasCloudflareSig = text.includes('cloudflare') || text.includes('ray id');

          // Challenge si : marqueurs DOM challenge OU (cloudflare + phrase humain)
          if (challengeDom || (hasCloudflareSig && hasInterstitialText)) {
            if (iframe) iframe.scrollIntoView({ block: 'center', behavior: 'instant' });
            return 'challenge';
          }

          // Iframe seul, sans contexte interstitiel → embedded
          if (iframe) {
            iframe.scrollIntoView({ block: 'center', behavior: 'instant' });
            return 'embedded';
          }

          return 'none';
        }
        """;

    public TurnstileType detect(Page page) {
        try {
            Object r = page.evaluate(DETECT_JS);
            if (r == null) return TurnstileType.NONE;
            return switch (r.toString()) {
                case "embedded" -> TurnstileType.EMBEDDED;
                case "challenge" -> TurnstileType.CHALLENGE;
                default -> TurnstileType.NONE;
            };
        } catch (Exception e) {
            log.debug("[TURNSTILE-DETECT] {}", e.getMessage());
            return TurnstileType.NONE;
        }
    }
}