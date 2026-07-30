package com.elyadata.feature.scraper.antibot.captcha.external;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaChallenge;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaProvider;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolution;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolver;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolverException;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class TwoCaptchaSolver implements CaptchaSolver {

    private static final String BASE_URL = "https://2captcha.com";
    private static final long INITIAL_WAIT_MS = 15_000;
    private static final long POLL_INTERVAL_MS = 5_000;
    private static final long MAX_WAIT_MS = 180_000;
    private static final int HTTP_TIMEOUT_SECONDS = 15;

    private static final Set<CaptchaType> SUPPORTED = EnumSet.of(
            CaptchaType.RECAPTCHA_V2,
            CaptchaType.RECAPTCHA_V3,
            CaptchaType.HCAPTCHA,
            CaptchaType.TURNSTILE
    );

    @ConfigProperty(name = "captcha.two-captcha.api-key", defaultValue = "")
    String apiKey;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public CaptchaProvider provider() {
        return CaptchaProvider.TWO_CAPTCHA;
    }

    @Override
    public boolean supports(CaptchaType type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public CaptchaSolution solve(CaptchaChallenge challenge) {
        if (!isEnabled()) {
            throw CaptchaSolverException.invalidConfig("2Captcha API key is missing");
        }

        long startMs = System.currentTimeMillis();
        try {
            String taskId = submitTask(challenge);
            log.info("[2CAPTCHA] Task submitted id={} type={} job={}",
                    taskId, challenge.type(), challenge.jobId());
            Thread.sleep(INITIAL_WAIT_MS);

            while (System.currentTimeMillis() - startMs < MAX_WAIT_MS) {
                String token = pollResult(taskId);
                if (token != null) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("[2CAPTCHA] Solved type={} in {}ms (id={})",
                            challenge.type(), elapsed, taskId);
                    return CaptchaSolution.success(
                            token, provider(), elapsed, estimateCost(challenge.type()));
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }

            throw CaptchaSolverException.timeout(provider(), MAX_WAIT_MS);

        } catch (CaptchaSolverException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsed = System.currentTimeMillis() - startMs;
            log.warn("[2CAPTCHA] Interrupted while solving type={}", challenge.type());
            return CaptchaSolution.failure(provider(), elapsed, "Interrupted during solving");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.warn("[2CAPTCHA] Solve failed type={}: {}", challenge.type(), e.getMessage());
            return CaptchaSolution.failure(provider(), elapsed, e.getMessage());
        }
    }

    private String submitTask(CaptchaChallenge ch) throws Exception {
        String method = switch (ch.type()) {
            case RECAPTCHA_V2, RECAPTCHA_V3 -> "userrecaptcha";
            case HCAPTCHA -> "hcaptcha";
            case FUNCAPTCHA -> "funcaptcha";
            case ROTATE -> null;
            case TURNSTILE -> "turnstile";
            case IMAGE_TEXT, GEETEST, CLOUDFLARE_INTERSTITIAL, CUSTOM, UNKNOWN -> null;
        };

        StringBuilder body = new StringBuilder()
                .append("key=").append(urlEncode(apiKey))
                .append("&method=").append(method)
                .append("&pageurl=").append(urlEncode(ch.pageUrl()))
                .append("&json=1");

        if (ch.type() == CaptchaType.RECAPTCHA_V2) {
            body.append("&googlekey=").append(urlEncode(ch.siteKey()));
            if (ch.invisible()) {
                body.append("&invisible=1");
            }
        } else if (ch.type() == CaptchaType.RECAPTCHA_V3) {
            body.append("&googlekey=").append(urlEncode(ch.siteKey()))
                    .append("&version=v3")
                    .append("&action=").append(urlEncode(ch.action()))
                    .append("&min_score=").append(ch.minScore() != null ? ch.minScore() : 0.5);
        } else {
            body.append("&sitekey=").append(urlEncode(ch.siteKey()));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/in.php"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode json = mapper.readTree(resp.body());

        int status = json.path("status").asInt(0);
        String request = json.path("request").asText("");

        if (status != 1) {
            throw CaptchaSolverException.providerDown(provider(), "submit failed: " + request);
        }
        return request;
    }

    private String pollResult(String taskId) throws Exception {
        String url = BASE_URL + "/res.php?key=" + urlEncode(apiKey)
                + "&action=get&id=" + urlEncode(taskId) + "&json=1";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode json = mapper.readTree(resp.body());

        int status = json.path("status").asInt(0);
        String request = json.path("request").asText("");

        if (status == 1) {
            return request;
        }
        if ("CAPCHA_NOT_READY".equals(request)) {
            return null;
        }
        throw CaptchaSolverException.providerDown(provider(), "poll error: " + request);
    }

    private static double estimateCost(CaptchaType type) {
        return switch (type) {
            case IMAGE_TEXT, GEETEST, CLOUDFLARE_INTERSTITIAL, CUSTOM, UNKNOWN -> 0.0;
            case RECAPTCHA_V2, RECAPTCHA_V3, HCAPTCHA -> 0.00299;
            case FUNCAPTCHA -> 0.0;
            case ROTATE -> 0.0;
            case TURNSTILE -> 0.00179;
        };
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}