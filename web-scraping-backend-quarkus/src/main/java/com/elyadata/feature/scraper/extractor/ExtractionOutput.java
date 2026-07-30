package com.elyadata.feature.scraper.extractor;

import java.util.List;
import java.util.Map;

public record ExtractionOutput(List<Map<String, Object>> records, String rawContent) {

    public static ExtractionOutput of(List<Map<String, Object>> records) {
        return new ExtractionOutput(records, null);
    }

    public static ExtractionOutput raw(String rawContent) {
        return new ExtractionOutput(List.of(), rawContent);
    }

    public static ExtractionOutput empty() {
        return new ExtractionOutput(List.of(), null);
    }
}