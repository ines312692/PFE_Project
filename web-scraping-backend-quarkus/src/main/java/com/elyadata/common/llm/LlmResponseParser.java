package com.elyadata.common.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;


@Slf4j
public final class LlmResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmResponseParser() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseObjectArray(String raw) {
        String json = extractJson(raw);
        if (json.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            if (parsed instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            if (parsed instanceof Map<?, ?> map) {
                for (String key : List.of("data", "results", "items", "records")) {
                    if (map.get(key) instanceof List<?> inner) {
                        return (List<Map<String, Object>>) inner;
                    }
                }
                return List.of((Map<String, Object>) map);
            }
        } catch (Exception e) {
            log.warn("[LLM] Could not parse JSON array from response: {}", e.getMessage());
        }
        return List.of();
    }

    private static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String clean = raw.strip();
        int objStart = clean.indexOf('{');
        int arrStart = clean.indexOf('[');
        int start;
        char open;
        char close;

        if (objStart == -1 && arrStart == -1) {
            return "";
        }
        if (objStart == -1 || (arrStart != -1 && arrStart < objStart)) {
            start = arrStart;
            open = '[';
            close = ']';
        } else {
            start = objStart;
            open = '{';
            close = '}';
        }

        // Walk forward, counting brackets while respecting string literals
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return clean.substring(start, i + 1);
                }
            }
        }
        return "";
    }
}