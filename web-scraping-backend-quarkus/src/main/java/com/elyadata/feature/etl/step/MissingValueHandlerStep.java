package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@ApplicationScoped
public class MissingValueHandlerStep implements EtlStep {

    private static final String DEFAULT_FALLBACK = "N/A";

    @Override
    public EtlStepType type() {
        return EtlStepType.MISSING_VALUE_HANDLER;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {

        String fallback = param(params, "fallback", DEFAULT_FALLBACK);
        String strategy = param(params, "strategy", "fill");

        if ("drop_row".equals(strategy)) {
            return records.stream()
                    .filter(r -> r.values().stream().noneMatch(this::isEmpty))
                    .toList();
        }

        return records.stream().map(r -> fillRecord(r, fallback)).toList();
    }

    private Map<String, Object> fillRecord(Map<String, Object> entry, String fallback) {
        Map<String, Object> result = new LinkedHashMap<>();
        entry.forEach((k, v) -> result.put(k, isEmpty(v) ? fallback : v));
        return result;
    }

    private boolean isEmpty(Object v) {
        return v == null || (v instanceof String s && s.isBlank());
    }

    private String param(Map<String, Object> params, String key, String def) {
        Object val = params.get(key);
        return val instanceof String s ? s : def;
    }
}
