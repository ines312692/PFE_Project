package com.elyadata.feature.scraper.antibot.captcha.detection;

import com.elyadata.common.llm.LlmRateLimitException;
import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.microsoft.playwright.Page;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class LlmFallbackCaptchaDetector {

    private static final String PROMPT = """
            You are inspecting a screenshot of a web page to detect anti-bot challenges.

            Look carefully for ANY of these visual signals:
            - A "I'm not a robot" checkbox (reCAPTCHA v2)
            - A grid of images asking to select specific objects
            - A Cloudflare interstitial page ("Verifying you are human", "Checking your browser",
              "Please wait", possibly with a single "Verify" button on the main page,
              NOT inside an iframe widget)
            - A slider puzzle asking to drag a piece into a gap
            - A text input next to a distorted text image
            - A custom challenge with phrases like "Click the dog", "Select all cats",
              "Prove you are human", "Security check", "Verify your identity",
              where the interactive elements appear on the main page

            TYPE classification rules:
            - RECAPTCHA_V2     : Google reCAPTCHA widget (checkbox or image grid in iframe)
            - HCAPTCHA         : hCaptcha widget (checkbox or image grid in iframe)
            - TURNSTILE        : Cloudflare Turnstile widget (compact iframe with checkbox/spinner)
            - GEETEST          : GeeTest slider/puzzle widget
            - IMAGE_TEXT       : Distorted text image with a text input field
            - CLOUDFLARE_INTERSTITIAL : Cloudflare full-page interstitial (NOT a widget),
                                       usually with "Checking your browser" or a big Verify button
                                       on the main page body
            - CUSTOM           : Any other interactive anti-bot challenge that is NOT one of the above
                                 and where the interactive elements are on the main page
                                 (e.g. "Click the dog", custom slider on body, click-based puzzles)
            - UNKNOWN          : You see a challenge but cannot classify it confidently

            Respond with ONLY a JSON object, no prose, no markdown:
            {"captcha_present": true|false,
             "type": "RECAPTCHA_V2|RECAPTCHA_V3|HCAPTCHA|TURNSTILE|GEETEST|IMAGE_TEXT|CLOUDFLARE_INTERSTITIAL|CUSTOM|UNKNOWN",
             "confidence": 0.0-1.0,
             "reasoning": "<one short sentence>"}

            Set captcha_present=false if you see normal content (articles, products, results)
            with no verification challenge.
            """;

    private final VisionLlmClient visionClient;

    @ConfigProperty(name = "captcha.llm-fallback.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "captcha.llm-fallback.min-confidence", defaultValue = "0.7")
    double minConfidence;

    public LlmFallbackCaptchaDetector(VisionLlmClient visionClient) {
        this.visionClient = visionClient;
    }

    public Optional<CaptchaChallenge> detect(PageSnapshot snapshot, UUID jobId) {
        if (!enabled) return Optional.empty();
        if (!visionClient.isAvailable()) {
            log.debug("[CAPTCHA-LLM-FALLBACK] Vision client unavailable — skipping");
            return Optional.empty();
        }

        Page page = extractPage(snapshot);
        if (page == null) return Optional.empty();

        try {
            byte[] screenshot = page.screenshot();
            if (screenshot == null || screenshot.length < 1_000) {
                log.warn("[CAPTCHA-LLM-FALLBACK] Screenshot too small — skipping");
                return Optional.empty();
            }
            String b64 = Base64.getEncoder().encodeToString(screenshot);

            String raw = visionClient.analyzeImage(PROMPT, b64, "image/png");
            List<Map<String, Object>> parsed = LlmResponseParser.parseObjectArray(raw);
            if (parsed.isEmpty()) {
                log.warn("[CAPTCHA-LLM-FALLBACK] Empty parse result");
                return Optional.empty();
            }

            Map<String, Object> answer = parsed.getFirst();
            boolean present = Boolean.TRUE.equals(answer.get("captcha_present"));
            double confidence = toDouble(answer.get("confidence"));
            String typeName = answer.get("type") != null ? answer.get("type").toString() : "UNKNOWN";
            String reasoning = answer.get("reasoning") != null ? answer.get("reasoning").toString() : "";

            log.info("[CAPTCHA-LLM-FALLBACK] present={} type={} confidence={} reasoning='{}' job={}",
                    present, typeName, confidence, reasoning, jobId);

            if (!present || confidence < minConfidence) {
                return Optional.empty();
            }

            CaptchaType type = mapType(typeName);
            if (type == null) {
                log.info("[CAPTCHA-LLM-FALLBACK] LLM saw a challenge but type unknown — " +
                        "human intervention will be required for job={}", jobId);
                return Optional.empty();
            }

            if ((type == CaptchaType.CUSTOM || type == CaptchaType.UNKNOWN)
                    && hasCloudflareFrame(page)) {
                log.info("[CAPTCHA-LLM-FALLBACK] Remap {} -> CLOUDFLARE_INTERSTITIAL (cf frame present) job={}",
                        type, jobId);
                type = CaptchaType.CLOUDFLARE_INTERSTITIAL;
            }

            if (type.isTokenBased() && type != CaptchaType.CLOUDFLARE_INTERSTITIAL) {
                log.warn("[CAPTCHA-LLM-FALLBACK] LLM detected token-based type={} that DOM " +
                                "detectors missed — no siteKey available, escalating to human for job={}",
                        type, jobId);
                return Optional.empty();
            }

            log.warn("[CAPTCHA-LLM-FALLBACK] LLM detected {} that DOM detectors missed " +
                    "(confidence={}) on url={} job={}", type, confidence, snapshot.getUrl(), jobId);

            return Optional.of(CaptchaChallenge.builder()
                    .type(type)
                    .pageUrl(snapshot.getUrl())
                    .jobId(jobId)
                    .build());

        } catch (LlmRateLimitException rate) {
            log.warn("[CAPTCHA-LLM-FALLBACK] Rate limited — skipping fallback for job={}", jobId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[CAPTCHA-LLM-FALLBACK] Detection failed for job={}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean hasCloudflareFrame(Page page) {
        try {
            return page.frames().stream().anyMatch(f -> {
                String u = f.url();
                return u != null && (u.contains("challenges.cloudflare.com")
                        || u.contains("/cdn-cgi/challenge-platform/"));
            });
        } catch (Exception e) {
            return false;
        }
    }

    private static CaptchaType mapType(String name) {
        if (name == null) return null;
        try {
            return CaptchaType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static Page extractPage(PageSnapshot snapshot) {
        if (snapshot == null) return null;
        LiveBrowserContext ctx = snapshot.getBrowserContext();
        return ctx != null && ctx.isPlaywright() ? ctx.getPage() : null;
    }
}