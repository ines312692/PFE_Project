package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.*;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.domain.model.SchemaField;
import com.elyadata.feature.schema.domain.model.SchemaFieldType;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;


@Slf4j
@ApplicationScoped
public class ValidateFieldsStep implements EtlStep {

    private static final int TOP_VIOLATIONS_LIMIT = 10;

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final ThreadLocal<QualityReport> REPORT_HOLDER = new ThreadLocal<>();

    private final ExtractionSchemaPort schemaPort;
    private final ObjectMapper mapper;

    @Inject
    public ValidateFieldsStep(ExtractionSchemaPort schemaPort, ObjectMapper mapper) {
        this.schemaPort = schemaPort;
        this.mapper = mapper;
    }

    public static QualityReport drainReport() {
        QualityReport r = REPORT_HOLDER.get();
        REPORT_HOLDER.remove();
        return r;
    }

    @Override
    public EtlStepType type() {
        return EtlStepType.VALIDATE_FIELDS;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {
        long start = System.currentTimeMillis();
        Map<String, Object> p = params == null ? Map.of() : params;

        List<ValidationRule> rules = resolveRules(records, p);
        if (rules.isEmpty()) {
            log.warn("[VALIDATE_FIELDS] No rules resolved — skipping validation");
            REPORT_HOLDER.set(QualityReport.empty());
            return records;
        }

        Map<String, FieldAccumulator> perField = new LinkedHashMap<>();
        List<RuleViolation> violations = new ArrayList<>();
        int invalidRecords = 0;

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            boolean recordInvalid = false;

            for (ValidationRule rule : rules) {
                FieldAccumulator acc = perField.computeIfAbsent(
                        rule.field(), FieldAccumulator::new);
                acc.totalChecks++;

                Object value = record.get(rule.field());
                boolean isMissing = (value == null) ||
                        (value instanceof String s && s.isBlank());

                if (isMissing) {
                    acc.missing++;
                    if (rule.isRequired()) {
                        acc.recordFailure("required");
                        violations.add(new RuleViolation(i, rule.field(), "required",
                                null, "field is missing or blank"));
                        recordInvalid = true;
                    } else {
                        acc.passed++;
                    }
                    continue;
                }

                List<RuleViolation> recordViolations =
                        checkValue(i, rule, value, record);
                if (recordViolations.isEmpty()) {
                    acc.passed++;
                } else {
                    recordViolations.forEach(v -> acc.recordFailure(v.rule()));
                    violations.addAll(recordViolations);
                    recordInvalid = true;
                }
            }

            if (recordInvalid) invalidRecords++;
        }

        QualityReport report = buildReport(records.size(), invalidRecords,
                perField, violations, System.currentTimeMillis() - start);

        log.info("[VALIDATE_FIELDS] {} record(s), {} valid, {} invalid, {} violation(s) — {}ms",
                report.totalRecords(), report.validRecords(),
                report.invalidRecords(), violations.size(), report.durationMs());

        REPORT_HOLDER.set(report);
        return records;
    }

    private List<ValidationRule> resolveRules(List<Map<String, Object>> records,
                                              Map<String, Object> params) {
        List<ValidationRule> explicit = readRules(params.get("rules"));
        Set<String> explicitFields = new HashSet<>();
        explicit.forEach(r -> explicitFields.add(r.field()));

        List<ValidationRule> schemaDerived = readSchemaRules(params.get("schemaId"));

        List<ValidationRule> merged = new ArrayList<>(explicit);
        for (ValidationRule r : schemaDerived) {
            if (!explicitFields.contains(r.field())) merged.add(r);
        }

        if (merged.isEmpty() && !records.isEmpty()) {
            merged = inferRulesFromFirstRecord(records.getFirst());
        }
        return merged;
    }

    private List<ValidationRule> readRules(Object raw) {
        if (raw == null) return List.of();
        try {
            return mapper.convertValue(raw, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[VALIDATE_FIELDS] Could not parse 'rules' param: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ValidationRule> readSchemaRules(Object schemaIdRaw) {
        if (schemaIdRaw == null) return List.of();
        UUID schemaId;
        try {
            schemaId = UUID.fromString(schemaIdRaw.toString());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        Optional<ExtractionSchema> schemaOpt = schemaPort.findById(schemaId);
        if (schemaOpt.isEmpty()) return List.of();

        return schemaOpt.get().fields().stream()
                .map(ValidateFieldsStep::ruleFromSchemaField)
                .toList();
    }

    private static ValidationRule ruleFromSchemaField(SchemaField f) {
        return new ValidationRule(
                f.name(),
                f.required(),
                mapSchemaType(f.type()),
                null, null, null, null, null, null);
    }

    private static String mapSchemaType(SchemaFieldType t) {
        if (t == null) return null;
        return switch (t.name().toUpperCase(Locale.ROOT)) {
            case "STRING", "TEXT" -> "string";
            case "INTEGER", "INT", "LONG" -> "integer";
            case "NUMBER", "DECIMAL", "FLOAT", "DOUBLE" -> "number";
            case "BOOLEAN", "BOOL" -> "boolean";
            case "DATE", "DATETIME", "TIMESTAMP" -> "date";
            case "URL", "LINK" -> "url";
            case "EMAIL" -> "email";
            default -> null;
        };
    }

    private List<ValidationRule> inferRulesFromFirstRecord(Map<String, Object> first) {
        return first.keySet().stream()
                .map(k -> new ValidationRule(k, false, null, null, null, null,
                        null, null, null))
                .toList();
    }

    private List<RuleViolation> checkValue(int recordIdx, ValidationRule rule,
                                           Object value, Map<String, Object> record) {
        List<RuleViolation> violations = new ArrayList<>();
        String field = rule.field();

        if (rule.hasTypeCheck() && !matchesType(value, rule.type())) {
            violations.add(new RuleViolation(recordIdx, field, "type", value,
                    "expected " + rule.type()));
        }
        if (rule.hasRegexCheck() && value instanceof String s
                && !Pattern.compile(rule.regex()).matcher(s).matches()) {
            violations.add(new RuleViolation(recordIdx, field, "regex", value,
                    "does not match pattern " + rule.regex()));
        }
        if (rule.hasRangeCheck()) {
            checkRange(recordIdx, rule, value).ifPresent(violations::add);
        }
        if (rule.hasEnumCheck() && !rule.allowedValues().contains(value.toString())) {
            violations.add(new RuleViolation(recordIdx, field, "enum", value,
                    "value not in allowed set"));
        }
        if (rule.hasCrossCheck()) {
            checkCrossField(recordIdx, rule, value, record).ifPresent(violations::add);
        }
        return violations;
    }

    private boolean matchesType(Object value, String type) {
        String s = value.toString();
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "string" -> value instanceof String;
            case "integer" -> isIntegerLike(s);
            case "number" -> isNumberLike(s);
            case "boolean" -> value instanceof Boolean
                    || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false");
            case "date" -> isIsoDate(s);
            case "url" -> URL_PATTERN.matcher(s).matches();
            case "email" -> EMAIL_PATTERN.matcher(s).matches();
            default -> true;
        };
    }

    private static boolean isIntegerLike(String s) {
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNumberLike(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isIsoDate(String s) {
        if (!ISO_DATE_PATTERN.matcher(s).matches()) return false;
        try {
            LocalDate.parse(s);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private Optional<RuleViolation> checkRange(int recordIdx, ValidationRule rule, Object value) {
        Double numeric = asDouble(value);
        if (numeric != null) {
            if (rule.min() != null && numeric < rule.min()) {
                return Optional.of(new RuleViolation(recordIdx, rule.field(), "min",
                        value, "below minimum " + rule.min()));
            }
            if (rule.max() != null && numeric > rule.max()) {
                return Optional.of(new RuleViolation(recordIdx, rule.field(), "max",
                        value, "above maximum " + rule.max()));
            }
            return Optional.empty();
        }
        // string-length fallback
        int len = value.toString().length();
        if (rule.min() != null && len < rule.min()) {
            return Optional.of(new RuleViolation(recordIdx, rule.field(), "minLength",
                    value, "length below " + rule.min().intValue()));
        }
        if (rule.max() != null && len > rule.max()) {
            return Optional.of(new RuleViolation(recordIdx, rule.field(), "maxLength",
                    value, "length above " + rule.max().intValue()));
        }
        return Optional.empty();
    }

    private Optional<RuleViolation> checkCrossField(int recordIdx, ValidationRule rule,
                                                    Object value, Map<String, Object> record) {
        Object other = record.get(rule.crossField());
        if (other == null) return Optional.empty();
        Double a = asDouble(value), b = asDouble(other);
        if (a == null || b == null) {
            return compareStrings(recordIdx, rule, value.toString(), other.toString());
        }
        boolean ok = switch (rule.crossOp().toLowerCase(Locale.ROOT)) {
            case "gt" -> a > b;
            case "gte" -> a >= b;
            case "lt" -> a < b;
            case "lte" -> a <= b;
            case "eq" -> a.equals(b);
            case "neq" -> !a.equals(b);
            default -> true;
        };
        return ok ? Optional.empty() : Optional.of(new RuleViolation(
                recordIdx, rule.field(), "crossField", value,
                rule.field() + " " + rule.crossOp() + " " + rule.crossField() + " failed"));
    }

    private Optional<RuleViolation> compareStrings(int recordIdx, ValidationRule rule,
                                                   String a, String b) {
        int cmp = a.compareTo(b);
        boolean ok = switch (rule.crossOp().toLowerCase(Locale.ROOT)) {
            case "gt" -> cmp > 0;
            case "gte" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "lte" -> cmp <= 0;
            case "eq" -> cmp == 0;
            case "neq" -> cmp != 0;
            default -> true;
        };
        return ok ? Optional.empty() : Optional.of(new RuleViolation(
                recordIdx, rule.field(), "crossField", a,
                rule.field() + " " + rule.crossOp() + " " + rule.crossField() + " failed"));
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private QualityReport buildReport(int total, int invalid,
                                      Map<String, FieldAccumulator> perField,
                                      List<RuleViolation> violations,
                                      long durationMs) {
        Map<String, FieldQuality> byField = new LinkedHashMap<>();
        perField.forEach((field, acc) -> byField.put(field, acc.toFieldQuality()));

        List<RuleViolation> top = violations.size() > TOP_VIOLATIONS_LIMIT
                ? violations.subList(0, TOP_VIOLATIONS_LIMIT)
                : violations;

        int valid = total - invalid;
        double rate = total == 0 ? 1.0 : (double) valid / total;

        return new QualityReport(total, valid, invalid, rate,
                byField, top, durationMs);
    }


    private static final class FieldAccumulator {
        final String field;
        int totalChecks, passed, failed, missing;
        final Map<String, Integer> failuresByRule = new LinkedHashMap<>();

        FieldAccumulator(String field) {
            this.field = field;
        }

        void recordFailure(String rule) {
            failed++;
            failuresByRule.merge(rule, 1, Integer::sum);
        }

        FieldQuality toFieldQuality() {
            int present = totalChecks - missing;
            double completeness = totalChecks == 0 ? 1.0
                    : (double) present / totalChecks;
            double validity = present == 0 ? 1.0
                    : (double) (present - (failed - missing)) / present;
            validity = Math.max(0.0, Math.min(1.0, validity));
            return new FieldQuality(field, totalChecks, passed, failed, missing,
                    completeness, validity, Map.copyOf(failuresByRule));
        }
    }
}