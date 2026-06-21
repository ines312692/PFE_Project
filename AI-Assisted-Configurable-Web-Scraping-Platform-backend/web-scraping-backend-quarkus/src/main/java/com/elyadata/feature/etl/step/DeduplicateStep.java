package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;


@ApplicationScoped
public class DeduplicateStep implements EtlStep {

    @Override
    public EtlStepType type() {
        return EtlStepType.DEDUPLICATE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {

        List<String> keys = params.get("keys") instanceof List<?> raw
                ? (List<String>) raw
                : List.of();

        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> unique = new ArrayList<>();
        for (Map<String, Object> entry : records) {
            String fingerprint = buildFingerprint(entry, keys);
            if (seen.add(fingerprint)) unique.add(entry);
        }
        return unique;
    }

    private String buildFingerprint(Map<String, Object> entry, List<String> keys) {
        if (keys.isEmpty()) return entry.toString();
        return keys.stream()
                .map(k -> String.valueOf(entry.getOrDefault(k, "")))
                .reduce((a, b) -> a + "|" + b)
                .orElse(entry.toString());
    }
}
