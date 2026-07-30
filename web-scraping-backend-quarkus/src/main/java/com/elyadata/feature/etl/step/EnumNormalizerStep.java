package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EnumNormalizerStep implements EtlStep {

    @Override
    public EtlStepType type() {
        return EtlStepType.NORMALIZE_ENUM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {

        List<String> fields = params.get("fields") instanceof List<?> raw
                ? (List<String>) raw
                : autoDetectEnumFields(records);

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
                result.put(f, normalize(s));
            }
        });
        return result;
    }

    private String normalize(String value) {
        return value.strip()
                .toLowerCase()
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("_+", "_");
    }

    private List<String> autoDetectEnumFields(List<Map<String, Object>> records) {
        if (records.isEmpty()) return List.of();
        return records.getFirst().keySet().stream()
                .filter(k -> k.matches("(?i).*(status|type|category|state|kind|mode).*"))
                .toList();
    }
}
