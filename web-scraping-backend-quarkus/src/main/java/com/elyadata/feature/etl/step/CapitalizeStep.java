package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CapitalizeStep implements EtlStep {

    @Override
    public EtlStepType type() {
        return EtlStepType.CAPITALIZE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {

        List<String> fields = params.get("fields") instanceof List<?> raw
                ? (List<String>) raw
                : List.of();

        String mode = params.get("mode") instanceof String m ? m : "title";

        return records.stream()
                .map(r -> processRecord(r, fields, mode))
                .toList();
    }

    private Map<String, Object> processRecord(Map<String, Object> row,
                                              List<String> fields, String mode) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        row.forEach((k, v) -> {
            if (v instanceof String s && !s.isBlank()
                    && (fields.isEmpty() || fields.contains(k))) {
                result.put(k, transform(s, mode));
            }
        });
        return result;
    }

    private String transform(String s, String mode) {
        return switch (mode) {
            case "upper" -> s.toUpperCase();
            case "lower" -> s.toLowerCase();
            default -> toTitleCase(s);
        };
    }

    private String toTitleCase(String s) {
        if (s.isBlank()) return s;
        String[] words = s.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().stripTrailing();
    }
}
