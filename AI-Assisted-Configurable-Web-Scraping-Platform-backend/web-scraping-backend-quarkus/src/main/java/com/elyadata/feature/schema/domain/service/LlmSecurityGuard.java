package com.elyadata.feature.schema.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class LlmSecurityGuard {

    public ValidationResult validateInput(String prompt, String outputFmt, UUID jobId, UUID userId) {

        if (prompt == null || prompt.isBlank()) {
            return deny("EMPTY_PROMPT", "Prompt must not be empty", jobId, userId, null);
        }

        String sanitized = LlmSecurityGuardRules.sanitize(prompt);

        if (sanitized.length() > LlmSecurityGuardRules.MAX_PROMPT_LENGTH) {
            return deny("PROMPT_TOO_LONG",
                    "Prompt exceeds maximum length of " + LlmSecurityGuardRules.MAX_PROMPT_LENGTH + " characters "
                            + "(received " + sanitized.length() + ")",
                    jobId, userId, sanitized);
        }

        if (outputFmt != null && outputFmt.length() > LlmSecurityGuardRules.MAX_OUTPUT_FORMAT_LEN) {
            return deny("OUTPUT_FORMAT_TOO_LONG",
                    "Output format exceeds " + LlmSecurityGuardRules.MAX_OUTPUT_FORMAT_LEN + " characters",
                    jobId, userId, sanitized);
        }

        for (Pattern p : LlmSecurityGuardRules.INJECTION_PATTERNS) {
            if (p.matcher(sanitized).find()) {
                return deny("PROMPT_INJECTION",
                        "Prompt injection pattern detected: " + p.pattern(),
                        jobId, userId, sanitized);
            }
        }

        for (Pattern p : LlmSecurityGuardRules.FORBIDDEN_PATTERNS) {
            if (p.matcher(sanitized).find()) {
                return deny("FORBIDDEN_CONTENT",
                        "Prompt contains forbidden content pattern",
                        jobId, userId, sanitized);
            }
        }

        audit("ALLOWED", null, jobId, userId, sanitized);
        return ValidationResult.allowed(sanitized);
    }

    public List<Map<String, Object>> validateOutput(List<Map<String, Object>> records, UUID jobId) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        if (records.size() > LlmSecurityGuardRules.MAX_RECORDS_IN_RESPONSE) {
            log.warn("[LLM-SECURITY] Output truncated: {} records exceeds max {}, job={}",
                    records.size(), LlmSecurityGuardRules.MAX_RECORDS_IN_RESPONSE, jobId);
            records = records.subList(0, LlmSecurityGuardRules.MAX_RECORDS_IN_RESPONSE);
        }

        return records.stream()
                .map(LlmSecurityGuardRules::sanitizeRecord)
                .toList();
    }

    private ValidationResult deny(String code, String reason, UUID jobId, UUID userId, String prompt) {
        log.warn("[LLM-SECURITY] BLOCKED code={} job={} user={} reason=\"{}\"",
                code, jobId, userId, reason);
        audit(code, reason, jobId, userId, prompt);
        return ValidationResult.denied(code, reason);
    }

    private void audit(String verdict, String reason, UUID jobId, UUID userId, String prompt) {
        String hash = prompt != null ? sha256(prompt) : "—";
        log.info("[LLM-AUDIT] verdict={} job={} user={} prompt_hash={} reason=\"{}\" ts={}",
                verdict, jobId, userId, hash, reason != null ? reason : "ok", Instant.now());
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16) + "…";
        } catch (Exception e) {
            return "hash-error";
        }
    }

    public record ValidationResult(
            boolean allowed,
            String sanitizedPrompt,
            String errorCode,
            String errorMessage
    ) {
        static ValidationResult allowed(String sanitized) {
            return new ValidationResult(true, sanitized, null, null);
        }

        static ValidationResult denied(String code, String message) {
            return new ValidationResult(false, null, code, message);
        }

        public boolean isAllowed() {
            return allowed;
        }
    }
}