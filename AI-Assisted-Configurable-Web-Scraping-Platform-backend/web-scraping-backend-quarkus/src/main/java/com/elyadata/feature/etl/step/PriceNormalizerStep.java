package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class PriceNormalizerStep implements EtlStep {

    private static final Pattern PRICE_PATTERN =
            Pattern.compile("[^\\d.,]*(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,2})?)\\D*");

    @Override
    public EtlStepType type() {
        return EtlStepType.NORMALIZE_PRICE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {
        List<String> fields = params.get("fields") instanceof List<?> raw
                ? (List<String>) raw
                : autoDetectPriceFields(records);
        return records.stream()
                .map(r -> normalizeRecord(r, fields))
                .toList();
    }

    private Map<String, Object> normalizeRecord(Map<String, Object> entry,
                                                List<String> fields) {
        Map<String, Object> result = new LinkedHashMap<>(entry);
        fields.forEach(f -> {
            Object val = result.get(f);
            if (val instanceof String s && !s.isBlank()) {
                result.put(f, parsePrice(s.strip()).orElse(s));
            }
        });
        return result;
    }

    private Optional<String> parsePrice(String raw) {
        Matcher m = PRICE_PATTERN.matcher(raw);
        if (!m.matches()) return Optional.empty();

        String numeric = m.group(1);
        boolean europeanFormat = numeric.contains(",")
                && numeric.lastIndexOf(',') > numeric.lastIndexOf('.');

        String normalized = europeanFormat
                ? numeric.replace(".", "").replace(",", ".")
                : numeric.replace(",", "");
        try {
            double val = Double.parseDouble(normalized);
            return Optional.of(String.format(Locale.US, "%.2f", val));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<String> autoDetectPriceFields(List<Map<String, Object>> records) {
        if (records.isEmpty()) return List.of();
        return records.getFirst().keySet().stream()
                .filter(k -> k.matches("(?i).*(price|cost|amount|fee|rate|total|value).*"))
                .toList();
    }
}