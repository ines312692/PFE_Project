package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.scraper.model.FieldConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExtractionUtils {

    private ExtractionUtils() {
    }

    public static List<Map<String, Object>> buildRecords(
            List<FieldConfig> fields,
            Map<String, List<String>> fieldValues,
            int maxCount) {

        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (FieldConfig f : fields) {
                List<String> vals = fieldValues.getOrDefault(f.getName(), List.of());
                entry.put(f.getName(), i < vals.size() ? vals.get(i) : null);
            }
            boolean hasData = entry.values().stream()
                    .anyMatch(v -> v != null && !v.toString().isBlank());
            if (hasData) records.add(entry);
        }
        return records;
    }
}