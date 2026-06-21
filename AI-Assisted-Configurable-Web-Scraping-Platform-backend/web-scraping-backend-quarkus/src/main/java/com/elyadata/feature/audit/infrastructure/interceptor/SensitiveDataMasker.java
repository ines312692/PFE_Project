package com.elyadata.feature.audit.infrastructure.interceptor;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class SensitiveDataMasker {

    private static final String MASK = "***";

    private static final Pattern SENSITIVE_JSON_FIELDS = Pattern.compile(
            "\"(password|currentPassword|newPassword|secret|clientSecret|webhookSecret|" +
                    "apiKey|api_key|token|accessToken|refreshToken|authorization|bearer)\"\\s*:\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);

    public static String mask(String body) {
        if (body == null || body.isBlank()) return body;
        return SENSITIVE_JSON_FIELDS.matcher(body)
                .replaceAll(m -> "\"" + m.group(1) + "\":\"" + MASK + "\"");
    }
}