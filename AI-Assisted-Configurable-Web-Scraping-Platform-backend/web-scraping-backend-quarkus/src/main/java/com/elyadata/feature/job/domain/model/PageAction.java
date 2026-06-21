package com.elyadata.feature.job.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;


@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageAction(
        PageActionType type,
        String selector,
        String value,
        Integer timeoutMs,
        Boolean optional,
        String label
) {

    public static final int DEFAULT_WAIT_TIMEOUT_MS = 15_000;
    public static final long MAX_WAIT_MS = 30_000L;

    @JsonIgnore
    public boolean isValid() {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case CLICK, WAIT_FOR_SELECTOR, HOVER -> hasText(selector);
            case FILL, TYPE, SELECT, PRESS_KEY -> hasText(selector) && hasText(value);
            case LLM_CLICK -> hasText(value);
            case EVAL_JS -> hasText(value);
            case WAIT_MS -> parseWaitMs() > 0;
            case SCROLL_TO_BOTTOM -> true;
        };
    }

    @JsonIgnore
    public boolean isOptional() {
        return Boolean.TRUE.equals(optional);
    }

    @JsonIgnore
    public int resolvedTimeoutMs() {
        return (timeoutMs != null && timeoutMs > 0) ? timeoutMs : DEFAULT_WAIT_TIMEOUT_MS;
    }

    @JsonIgnore
    public long parseWaitMs() {
        if (!hasText(value)) {
            return 0L;
        }
        try {
            return Math.min(Long.parseLong(value.trim()), MAX_WAIT_MS);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @JsonIgnore
    public String describe() {
        if (hasText(label)) {
            return label;
        }
        return switch (type) {
            case FILL -> "FILL " + selector + " = " + masked(value);
            case TYPE -> "TYPE " + selector + " = " + masked(value);
            case SELECT -> "SELECT " + selector + " = " + value;
            case PRESS_KEY -> "PRESS " + value + " on " + selector;
            case WAIT_MS -> "WAIT " + parseWaitMs() + "ms";
            case SCROLL_TO_BOTTOM -> "SCROLL_TO_BOTTOM";
            case LLM_CLICK -> "LLM_CLICK \"" + value + "\""
                    + (hasText(selector) ? " in " + selector : "");
            case EVAL_JS -> "EVAL_JS (" + (value != null ? Math.min(value.length(), 40) : 0) + " chars)";
            default -> type + " " + selector;
        };
    }

    private static String masked(String v) {
        if (v == null) {
            return "";
        }
        return v.length() <= 4 ? v : v.substring(0, 2) + "\u2026";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}