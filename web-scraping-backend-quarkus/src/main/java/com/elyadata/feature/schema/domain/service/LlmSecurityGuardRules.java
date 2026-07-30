package com.elyadata.feature.schema.domain.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class LlmSecurityGuardRules {

    static final int MAX_PROMPT_LENGTH = 2_000;
    static final int MAX_OUTPUT_FORMAT_LEN = 1_000;
    static final int MAX_RECORDS_IN_RESPONSE = 500;
    static final int MAX_FIELD_VALUE_LENGTH = 5_000;

    static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(previous|all|above)\\s+instructions?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+(dan|jailbreak|evil|unrestricted)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(everything|all|your|the\\s+system)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt\\s*(override|bypass|ignore)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\[\\s*INST\\s*\\]|\\[\\s*SYS\\s*\\]|<\\|im_start\\|>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("do\\s+anything\\s+now|DAN\\s+mode", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new\\s+instructions?\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+(you\\s+are|to\\s+be)\\s+", Pattern.CASE_INSENSITIVE)
    );

    static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("(password|passwd|secret|api[_\\s]?key|token|credential)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("credit\\s*card|cvv|iban|social\\s*security|ssn|passport\\s*(number|no)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(email|phone|address|date\\s+of\\s+birth).*extract.*all", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script|javascript:|data:text/html|eval\\s*\\(|document\\.cookie", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rm\\s+-rf|DROP\\s+TABLE|DELETE\\s+FROM|exec\\s*\\(|system\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("base64\\s*(decode|encode)|atob\\s*\\(|btoa\\s*\\(", Pattern.CASE_INSENSITIVE)
    );

    static final Pattern SCRIPT_IN_VALUE =
            Pattern.compile("<script|javascript:|on(load|click|error|mouseover)\\s*=", Pattern.CASE_INSENSITIVE);

    private LlmSecurityGuardRules() {
    }

    static String sanitize(String input) {
        return input
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("\\s{3,}", "  ")
                .strip();
    }

    static Map<String, Object> sanitizeRecord(Map<String, Object> input) {
        var cleaned = new LinkedHashMap<String, Object>();

        for (var entry : input.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof String str) {
                if (str.length() > LlmSecurityGuardRules.MAX_FIELD_VALUE_LENGTH) {
                    str = str.substring(0, LlmSecurityGuardRules.MAX_FIELD_VALUE_LENGTH);
                }

                if (SCRIPT_IN_VALUE.matcher(str).find()) {
                    str = str.replaceAll("(?i)<script[^>]*>.*?</script>", "")
                            .replaceAll("(?i)javascript:", "")
                            .strip();
                }

                cleaned.put(entry.getKey(), str);
            } else {
                cleaned.put(entry.getKey(), value);
            }
        }

        return cleaned;
    }
}