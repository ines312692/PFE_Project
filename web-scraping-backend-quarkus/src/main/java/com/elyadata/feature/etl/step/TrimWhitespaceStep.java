package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TrimWhitespaceStep implements EtlStep {

    @Override
    public EtlStepType type() {
        return EtlStepType.TRIM_WHITESPACE;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {
        return records.stream().map(this::trimRecord).toList();
    }

    private Map<String, Object> trimRecord(Map<String, Object> entry) {
        Map<String, Object> clean = new LinkedHashMap<>();
        entry.forEach((k, v) -> clean.put(k, v instanceof String s ? s.strip() : v));
        return clean;
    }
}
