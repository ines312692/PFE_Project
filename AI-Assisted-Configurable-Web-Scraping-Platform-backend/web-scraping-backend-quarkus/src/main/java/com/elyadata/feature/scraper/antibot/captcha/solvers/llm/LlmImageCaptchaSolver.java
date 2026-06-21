package com.elyadata.feature.scraper.antibot.captcha.solvers.llm;

import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.LlmUnavailableException;
import com.elyadata.common.llm.VisionLlmClient;
import com.elyadata.feature.scraper.antibot.captcha.core.*;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LlmImageCaptchaSolver implements CaptchaSolver {

    private static final double MIN_CONFIDENCE = 0.50;
    private static final int VOTING_PASSES = 3;
    private static final int MIN_AGREEMENT = 2;

    private final VisionLlmClient visionClient;
    private final CaptchaImagePreprocessor preprocessor;
    private final CaptchaPromptFactory prompts;

    @ConfigProperty(name = "captcha.llm.voting-enabled", defaultValue = "true")
    boolean votingEnabled;

    @Override
    public CaptchaProvider provider() {
        return CaptchaProvider.LLM_VISION;
    }

    @Override
    public boolean supports(CaptchaType type) {
        return type == CaptchaType.IMAGE_TEXT;
    }

    @Override
    public boolean isEnabled() {
        return visionClient.isAvailable();
    }

    @Override
    public CaptchaSolution solve(CaptchaChallenge challenge) {
        if (!isEnabled()) {
            throw CaptchaSolverException.invalidConfig("LLM vision client is not configured");
        }

        long startMs = System.currentTimeMillis();
        try {
            byte[] enhanced = preprocessor.enhance(
                    Base64.getDecoder().decode(challenge.imageBase64()));
            String enhancedB64 = Base64.getEncoder().encodeToString(enhanced);

            String kind = classify(enhancedB64);
            log.info("[CAPTCHA-LLM] Classified as {} for job={}", kind, challenge.jobId());

            String prompt = "MATH".equalsIgnoreCase(kind)
                    ? prompts.mathCaptchaPrompt()
                    : prompts.textCaptchaPrompt();

            VotingResult voting = solveWithVoting(prompt, enhancedB64, challenge.jobId());
            long elapsed = System.currentTimeMillis() - startMs;

            if (voting.answer == null || voting.answer.isBlank()) {
                return CaptchaSolution.failure(provider(), elapsed,
                        "LLM voting yielded no consensus");
            }
            if (voting.confidence < MIN_CONFIDENCE) {
                log.warn("[CAPTCHA-LLM] Low confidence '{}' ({}) job={} — rejected",
                        voting.answer, voting.confidence, challenge.jobId());
                return CaptchaSolution.failure(provider(), elapsed,
                        "Confidence " + voting.confidence + " < " + MIN_CONFIDENCE);
            }

            log.info("[CAPTCHA-LLM] Solved IMAGE_TEXT ({}) job={} answer='{}' conf={} votes={}/{} in {}ms",
                    kind, challenge.jobId(), voting.answer, voting.confidence,
                    voting.agreement, VOTING_PASSES, elapsed);
            return CaptchaSolution.success(voting.answer, provider(), elapsed, 0.0);

        } catch (LlmUnavailableException e) {
            return CaptchaSolution.failure(provider(),
                    System.currentTimeMillis() - startMs, "LLM unavailable: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[CAPTCHA-LLM] Solve failed job={}: {}", challenge.jobId(), e.getMessage());
            return CaptchaSolution.failure(provider(),
                    System.currentTimeMillis() - startMs, e.getMessage());
        }
    }

    private String classify(String imageB64) {
        try {
            String raw = visionClient.analyzeImage(prompts.classifierPrompt(), imageB64, "image/png");
            String norm = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            return norm.contains("MATH") ? "MATH" : "TEXT";
        } catch (Exception e) {
            log.warn("[CAPTCHA-LLM] Classification failed, defaulting to TEXT: {}", e.getMessage());
            return "TEXT";
        }
    }

    private VotingResult solveWithVoting(String prompt, String imageB64, UUID jobId) {
        int passes = votingEnabled ? VOTING_PASSES : 1;
        Map<String, Integer> votes = new HashMap<>();
        Map<String, Double> confidences = new HashMap<>();

        for (int i = 0; i < passes; i++) {
            try {
                String raw = visionClient.analyzeImage(prompt, imageB64, "image/png");
                Map<String, Object> parsed = firstObject(raw);
                String answer = parsed.get("answer") == null ? "" :
                        parsed.get("answer").toString().trim();
                double conf = toDouble(parsed.get("confidence"));

                if (!answer.isBlank()) {
                    votes.merge(answer, 1, Integer::sum);
                    confidences.merge(answer, conf, Math::max);
                    log.debug("[CAPTCHA-LLM] Pass {}/{} job={} → '{}' (conf={})",
                            i + 1, passes, jobId, answer, conf);
                }
            } catch (Exception e) {
                log.warn("[CAPTCHA-LLM] Voting pass {}/{} failed: {}", i + 1, passes, e.getMessage());
            }
        }

        if (votes.isEmpty()) return new VotingResult(null, 0.0, 0);

        Map.Entry<String, Integer> winner = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        if (votingEnabled && winner.getValue() < MIN_AGREEMENT) {
            log.warn("[CAPTCHA-LLM] No majority — top='{}' got {}/{} votes job={}",
                    winner.getKey(), winner.getValue(), passes, jobId);
            return new VotingResult(null, 0.0, winner.getValue());
        }

        return new VotingResult(
                winner.getKey(),
                confidences.getOrDefault(winner.getKey(), 0.0),
                winner.getValue());
    }

    private static Map<String, Object> firstObject(String raw) {
        List<Map<String, Object>> objs = LlmResponseParser.parseObjectArray(raw);
        return objs.isEmpty() ? Map.of() : objs.getFirst();
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? 0.0 : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private record VotingResult(String answer, double confidence, int agreement) {
    }
}