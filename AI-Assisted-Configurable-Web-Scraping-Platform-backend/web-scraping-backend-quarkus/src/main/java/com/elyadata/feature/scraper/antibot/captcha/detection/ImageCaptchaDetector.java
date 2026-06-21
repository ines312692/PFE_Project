package com.elyadata.feature.scraper.antibot.captcha.detection;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@ApplicationScoped
public class ImageCaptchaDetector {

    private static final String DETECT_SCRIPT = """
            () => {
              // Strict: only filename patterns, not full URL
              const FILENAME_RX = /(^|\\/)([a-z0-9_-]*captcha|securimage|kaptcha|botdetect|simplecaptcha)[^\\/]*\\.(png|jpg|jpeg|bmp|webp)(\\?|$)/i;
              const NEGATIVE_RX = /(logo|icon|sprite|banner|favicon|footer)/i;
              const INPUT_NAME_RX = /captcha|verify|securimage|kaptcha|imgcode/i;
              const IGNORED_PARENTS = ['HEADER', 'FOOTER', 'NAV', 'ASIDE'];
            
              const isInIgnoredParent = (el) => {
                let cur = el.parentElement;
                while (cur && cur !== document.body) {
                  if (IGNORED_PARENTS.includes(cur.tagName)) return true;
                  cur = cur.parentElement;
                }
                return false;
              };
            
              const findFormCaptchaInput = (img) => {
                const form = img.closest('form');
                if (!form) return null;
                const inputs = form.querySelectorAll('input[type="text"], input:not([type])');
                for (const inp of inputs) {
                  const sig = ((inp.name || '') + ' ' + (inp.id || '') + ' ' +
                               (inp.placeholder || '') + ' ' + (inp.className || ''));
                  if (INPUT_NAME_RX.test(sig)) return inp;
                }
                return null;
              };
            
              const buildInputSelector = (inp) => {
                if (!inp) return null;
                if (inp.id) return '#' + CSS.escape(inp.id);
                if (inp.name) return 'input[name="' + inp.name + '"]';
                return null;
              };
            
              const imgs = Array.from(document.querySelectorAll('img'));
              let bestCandidate = null;
              let bestScore = 0;
            
              for (const img of imgs) {
                const src = img.src || '';
                const w = img.naturalWidth || img.width;
                const h = img.naturalHeight || img.height;
            
                // Hard exclusions
                if (!src) continue;
                if (src.endsWith('.svg') || src.endsWith('.gif')) continue;
                if (NEGATIVE_RX.test(src)) continue;
                if (w < 60 || w > 400 || h < 20 || h > 150) continue;
                if (isInIgnoredParent(img)) continue;
            
                let score = 0;
                if (FILENAME_RX.test(src)) score += 3;
            
                const linkedInput = findFormCaptchaInput(img);
                if (linkedInput) score += 2;
            
                // Image immediately before input in DOM (typical captcha layout)
                if (linkedInput && img.compareDocumentPosition(linkedInput) &
                    Node.DOCUMENT_POSITION_FOLLOWING) {
                  score += 1;
                }
            
                if (score > bestScore) {
                  bestScore = score;
                  const rect = img.getBoundingClientRect();
                  bestCandidate = {
                    src: src,
                    selector: img.id ? '#' + CSS.escape(img.id) : null,
                    inputSelector: buildInputSelector(linkedInput),
                    score: score,
                    x: rect.x, y: rect.y, w: rect.width, h: rect.height
                  };
                }
              }
            
              // Threshold: at least filename match + linked input (3 + 2 = 5),
              // or filename match + DOM proximity (3 + 1 = 4)
              if (bestScore < 4) return null;
              return JSON.stringify(bestCandidate);
            }
            """;

    public Optional<CaptchaChallenge> detect(PageSnapshot snapshot, UUID jobId) {
        Page page = extractPage(snapshot);
        if (page == null) return Optional.empty();

        try {
            Object raw = page.evaluate(DETECT_SCRIPT);
            if (raw == null) return Optional.empty();

            Map<String, Object> info = parseJson(raw.toString());
            if (info == null) return Optional.empty();

            String src = (String) info.get("src");
            String inputSelector = (String) info.get("inputSelector");
            Object scoreObj = info.get("score");
            int score = scoreObj instanceof Number n ? n.intValue() : 0;

            log.info("[CAPTCHA-IMAGE-DETECT] Candidate src={} score={} input={} job={}",
                    src, score, inputSelector, jobId);

            byte[] imgBytes = downloadImage(page, src);
            if (imgBytes.length < 200) {
                log.warn("[CAPTCHA-IMAGE-DETECT] Image download failed or too small ({}b)",
                        imgBytes.length);
                return Optional.empty();
            }

            if (!isRasterImage(imgBytes)) {
                log.warn("[CAPTCHA-IMAGE-DETECT] Downloaded content is not a raster image — skipping");
                return Optional.empty();
            }

            return Optional.of(CaptchaChallenge.builder()
                    .type(CaptchaType.IMAGE_TEXT)
                    .pageUrl(snapshot.getUrl())
                    .jobId(jobId)
                    .imageBase64(Base64.getEncoder().encodeToString(imgBytes))
                    .inputSelector(inputSelector)
                    .build());
        } catch (Exception e) {
            log.warn("[CAPTCHA-IMAGE-DETECT] Detection failed: {}", e.getMessage());
            return Optional.empty();
        }
    }


    private static boolean isRasterImage(byte[] bytes) {
        if (bytes.length < 12) return false;
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return true;
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return true;
        }
        if (bytes[0] == 0x42 && bytes[1] == 0x4D) {
            return true;
        }
        return bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
    }

    private static byte[] downloadImage(Page page, String src) {
        try {
            Object result = page.evaluate("""
                    async (src) => {
                      const resp = await fetch(src, { credentials: 'include' });
                      const blob = await resp.blob();
                      const buf = await blob.arrayBuffer();
                      return Array.from(new Uint8Array(buf));
                    }
                    """, src);

            if (result instanceof List<?> list) {
                return toByteArray(list);
            }
            return new byte[0];
        } catch (Exception e) {
            log.warn("[CAPTCHA-IMAGE-DETECT] Image download failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    private static byte[] toByteArray(List<?> list) {
        byte[] out = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = ((Number) list.get(i)).byteValue();
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static Page extractPage(PageSnapshot snapshot) {
        if (snapshot == null) return null;
        LiveBrowserContext ctx = snapshot.getBrowserContext();
        return ctx != null && ctx.isPlaywright() ? ctx.getPage() : null;
    }
}