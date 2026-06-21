package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
public class HtmlStripperStep implements EtlStep {

    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    @Override
    public EtlStepType type() {
        return EtlStepType.STRIP_HTML;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {
        return records.stream().map(this::stripRecord).toList();
    }

    private Map<String, Object> stripRecord(Map<String, Object> entry) {
        Map<String, Object> clean = new LinkedHashMap<>();
        entry.forEach((k, v) -> clean.put(k, v instanceof String s ? strip(s) : v));
        return clean;
    }

    private String strip(String value) {
        String stripped = HTML_TAGS.matcher(value).replaceAll(" ");
        stripped = MULTI_SPACE.matcher(stripped).replaceAll(" ");
        return stripped.strip();
    }
}
