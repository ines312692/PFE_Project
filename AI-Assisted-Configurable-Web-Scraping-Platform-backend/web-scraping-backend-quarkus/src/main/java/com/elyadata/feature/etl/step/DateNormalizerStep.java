package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DateNormalizerStep implements EtlStep {

    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final List<DateTimeFormatter> PARSERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("d.MM.yyyy")
    );

    @Override
    public EtlStepType type() {
        return EtlStepType.NORMALIZE_DATE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {

        List<String> fields = params.get("fields") instanceof List<?> raw
                ? (List<String>) raw
                : autoDetectDateFields(records);

        return records.stream()
                .map(r -> normalizeRecord(r, fields))
                .toList();
    }

    private Map<String, Object> normalizeRecord(Map<String, Object> row,
                                                List<String> fields) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        fields.forEach(f -> {
            Object val = result.get(f);
            if (val instanceof String s && !s.isBlank()) {
                result.put(f, parseDate(s.strip()).orElse(s));
            }
        });
        return result;
    }

    private Optional<String> parseDate(String raw) {
        for (DateTimeFormatter fmt : PARSERS) {
            try {
                return Optional.of(LocalDate.parse(raw, fmt).format(OUTPUT));
            } catch (DateTimeParseException ignored) {
                // Expected: a format doesn't match, try the next one
            }
        }
        log.debug("[ETL/DateNormalizer] Could not parse date: {}", raw);
        return Optional.empty();
    }

    private List<String> autoDetectDateFields(List<Map<String, Object>> records) {
        if (records.isEmpty()) return List.of();
        return records.getFirst().keySet().stream()
                .filter(k -> k.matches("(?i).*(date|time|created|updated|at|on).*"))
                .toList();
    }
}