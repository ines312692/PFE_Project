package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@ApplicationScoped
public class PiiAnonymizationStep implements EtlStep {

    private static final String DEFAULT_MASK = "***";

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+\\d{1,3}[\\s.-]?\\d{1,4}(?:[\\s.-]?\\d{2,4}){2,4})" +
                    "|" +
                    "(?:\\b0[1-9](?:[\\s.-]?\\d{2}){4}\\b)");

    private static final Pattern IBAN = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}\\b");

    private static final Pattern CREDIT_CARD = Pattern.compile(
            "\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b");

    private static final Pattern SSN_FR = Pattern.compile(
            "\\b[12]\\s?\\d{2}\\s?(?:0[1-9]|1[0-2])\\s?(?:\\d{2}|2[AB])\\s?\\d{3}\\s?\\d{3}(?:\\s?\\d{2})?\\b");

    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");

    private static final Map<String, Pattern> CATEGORY_PATTERNS = Map.of(
            "EMAIL", EMAIL,
            "PHONE", PHONE,
            "IBAN", IBAN,
            "CREDIT_CARD", CREDIT_CARD,
            "SSN_FR", SSN_FR,
            "IPV4", IPV4);

    @Override
    public EtlStepType type() {
        return EtlStepType.ANONYMIZE_PII;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {
        Map<String, Object> p = params == null ? Map.of() : params;

        List<Pattern> patterns = resolvePatterns(p.get("categories"));
        MaskFormat fmt = resolveMaskFormat(p.get("maskFormat"));
        String mask = String.valueOf(p.getOrDefault("mask", DEFAULT_MASK));
        Set<String> only = readSet(p.get("fields"));
        Set<String> skip = readSet(p.get("skipFields"));

        long[] counter = {0L};
        List<Map<String, Object>> out = records.stream()
                .map(rec -> anonymize(rec, patterns, fmt, mask, only, skip, counter))
                .toList();

        log.info("[ANONYMIZE_PII] Redacted {} PII match(es) across {} record(s) " +
                        "(format={}, categories={})",
                counter[0], records.size(), fmt, patternNames(patterns));
        return out;
    }

    private Map<String, Object> anonymize(Map<String, Object> record,
                                          List<Pattern> patterns,
                                          MaskFormat fmt, String mask,
                                          Set<String> only, Set<String> skip,
                                          long[] counter) {
        Map<String, Object> clean = new LinkedHashMap<>();
        record.forEach((field, value) -> {
            boolean candidate = value instanceof String
                    && !skip.contains(field)
                    && (only.isEmpty() || only.contains(field));
            clean.put(field, candidate
                    ? redact((String) value, patterns, fmt, mask, counter)
                    : value);
        });
        return clean;
    }

    private String redact(String value, List<Pattern> patterns,
                          MaskFormat fmt, String mask, long[] counter) {
        String current = value;
        for (Pattern p : patterns) {
            Matcher m = p.matcher(current);
            if (!m.find()) continue;
            StringBuilder sb = new StringBuilder();
            do {
                counter[0]++;
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(applyMask(m.group(), fmt, mask)));
            } while (m.find());
            m.appendTail(sb);
            current = sb.toString();
        }
        return current;
    }

    private String applyMask(String match, MaskFormat fmt, String mask) {
        return switch (fmt) {
            case FULL -> mask;
            case PARTIAL -> match.length() <= 3
                    ? mask
                    : match.charAt(0) + mask + match.charAt(match.length() - 1);
            case HASH -> "[anon:" + shortHash(match) + "]";
        };
    }

    private static String shortHash(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes());
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "anon";
        }
    }

    private static List<Pattern> resolvePatterns(Object raw) {
        Set<String> requested = readSet(raw);
        if (requested.isEmpty()) return List.copyOf(CATEGORY_PATTERNS.values());
        return requested.stream()
                .map(c -> c.toUpperCase(Locale.ROOT))
                .map(CATEGORY_PATTERNS::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static MaskFormat resolveMaskFormat(Object raw) {
        try {
            return MaskFormat.valueOf(
                    String.valueOf(raw == null ? "PARTIAL" : raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MaskFormat.PARTIAL;
        }
    }

    private static Set<String> readSet(Object raw) {
        if (raw instanceof Collection<?> c) {
            Set<String> out = new HashSet<>();
            for (Object o : c) if (o != null) out.add(o.toString());
            return out;
        }
        return Set.of();
    }

    private static List<String> patternNames(List<Pattern> patterns) {
        List<String> names = new ArrayList<>();
        CATEGORY_PATTERNS.forEach((n, p) -> {
            if (patterns.contains(p)) names.add(n);
        });
        return names;
    }

    private enum MaskFormat {FULL, PARTIAL, HASH}
}