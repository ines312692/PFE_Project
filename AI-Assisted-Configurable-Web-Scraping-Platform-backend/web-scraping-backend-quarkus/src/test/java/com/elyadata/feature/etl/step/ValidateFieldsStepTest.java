package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import com.elyadata.feature.etl.domain.model.QualityReport;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidateFieldsStep")
class ValidateFieldsStepTest {

    @Mock
    ExtractionSchemaPort schemaPort;

    private ValidateFieldsStep step;

    @BeforeEach
    void setUp() {
        step = new ValidateFieldsStep(schemaPort, new ObjectMapper());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> rule(String field, Boolean required, String type) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("field", field);
        if (required != null) r.put("required", required);
        if (type != null)     r.put("type", type);
        return r;
    }

    private Map<String, Object> ruleWith(String field, Object... kvPairs) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("field", field);
        for (int i = 0; i < kvPairs.length; i += 2) {
            r.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return r;
    }

    private Map<String, Object> params(List<Map<String, Object>> rules) {
        return Map.of("rules", rules);
    }

    private QualityReport applyAndDrain(List<Map<String, Object>> records,
                                        Map<String, Object> params) {
        step.apply(records, params);
        return ValidateFieldsStep.drainReport();
    }

    // ─── type() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("type() returns VALIDATE_FIELDS")
    void type_returnsValidateFields() {
        assertThat(step.type()).isEqualTo(EtlStepType.VALIDATE_FIELDS);
    }

    // ─── no rules ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("when no rules can be resolved")
    class NoRules {

        @Test
        @DisplayName("returns records unchanged and stores empty report")
        void emptyParams_returnsUnchanged() {
            var records = List.of(Map.<String, Object>of("x", "1"));
            var result = step.apply(records, Map.of());
            assertThat(result).isEqualTo(records);
            QualityReport report = ValidateFieldsStep.drainReport();
            assertThat(report).isNotNull();
        }

        @Test
        @DisplayName("infers rules from first record when no explicit rules given")
        void infersRulesFromFirstRecord() {
            var records = List.of(Map.<String, Object>of("name", "Alice"));
            step.apply(records, Map.of());
            QualityReport r = ValidateFieldsStep.drainReport();
            assertThat(r.totalRecords()).isEqualTo(1);
        }
    }

    // ─── empty input ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("empty record list returns empty list")
    void emptyRecords_returnsEmpty() {
        var result = step.apply(List.of(), params(List.of(rule("name", true, "string"))));
        assertThat(result).isEmpty();
    }

    // ─── required field ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("required field validation")
    class RequiredField {

        @Test
        @DisplayName("required field present — valid record, no violations")
        void required_present_noViolation() {
            var records = List.of(Map.<String, Object>of("name", "Alice"));
            QualityReport r = applyAndDrain(records, params(List.of(rule("name", true, null))));
            assertThat(r.invalidRecords()).isZero();
            assertThat(r.topViolations()).isEmpty();
        }

        @Test
        @DisplayName("required field absent — one violation with rule='required'")
        void required_missing_violation() {
            List<Map<String, Object>> records = List.of(new HashMap<>());
            QualityReport r = applyAndDrain(records, params(List.of(rule("name", true, null))));
            assertThat(r.invalidRecords()).isEqualTo(1);
            assertThat(r.topViolations()).hasSize(1);
            assertThat(r.topViolations().get(0).rule()).isEqualTo("required");
            assertThat(r.topViolations().get(0).field()).isEqualTo("name");
        }

        @Test
        @DisplayName("required field blank — treated as missing")
        void required_blank_violation() {
            var records = List.of(Map.<String, Object>of("name", "   "));
            QualityReport r = applyAndDrain(records, params(List.of(rule("name", true, null))));
            assertThat(r.invalidRecords()).isEqualTo(1);
        }

        @Test
        @DisplayName("optional field absent — not a violation")
        void optional_missing_noViolation() {
            List<Map<String, Object>> records = List.of(new HashMap<>());
            QualityReport r = applyAndDrain(records, params(List.of(rule("name", false, null))));
            assertThat(r.invalidRecords()).isZero();
        }
    }

    // ─── type checks ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("type check")
    class TypeCheck {

        @Test
        @DisplayName("string type — String value passes")
        void string_passes() {
            var records = List.of(Map.<String, Object>of("f", "hello"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "string")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("integer type — whole number string passes")
        void integer_numericString_passes() {
            var records = List.of(Map.<String, Object>of("f", "42"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "integer")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("integer type — decimal string fails")
        void integer_decimal_fails() {
            var records = List.of(Map.<String, Object>of("f", "3.14"));
            QualityReport r = applyAndDrain(records, params(List.of(rule("f", false, "integer"))));
            assertThat(r.invalidRecords()).isEqualTo(1);
            assertThat(r.topViolations().get(0).rule()).isEqualTo("type");
        }

        @Test
        @DisplayName("number type — decimal string passes")
        void number_decimal_passes() {
            var records = List.of(Map.<String, Object>of("f", "3.14"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "number")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("number type — non-numeric string fails")
        void number_nonNumeric_fails() {
            var records = List.of(Map.<String, Object>of("f", "abc"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "number")))).invalidRecords()).isEqualTo(1);
        }

        @Test
        @DisplayName("boolean type — 'true' string passes")
        void boolean_trueString_passes() {
            var records = List.of(Map.<String, Object>of("f", "true"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "boolean")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("boolean type — Boolean true passes")
        void boolean_boxedBoolean_passes() {
            var records = List.of(Map.<String, Object>of("f", Boolean.TRUE));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "boolean")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("date type — valid ISO date passes")
        void date_validIso_passes() {
            var records = List.of(Map.<String, Object>of("f", "2024-01-15"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "date")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("date type — invalid date string fails")
        void date_invalid_fails() {
            var records = List.of(Map.<String, Object>of("f", "not-a-date"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "date")))).invalidRecords()).isEqualTo(1);
        }

        @Test
        @DisplayName("date type — impossible date (Feb 30) fails")
        void date_impossible_fails() {
            var records = List.of(Map.<String, Object>of("f", "2024-02-30"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "date")))).invalidRecords()).isEqualTo(1);
        }

        @Test
        @DisplayName("url type — https URL passes")
        void url_httpsValid_passes() {
            var records = List.of(Map.<String, Object>of("f", "https://example.com/path"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "url")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("url type — bare domain without protocol fails")
        void url_bareDomain_fails() {
            var records = List.of(Map.<String, Object>of("f", "example.com"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "url")))).invalidRecords()).isEqualTo(1);
        }

        @Test
        @DisplayName("email type — valid address passes")
        void email_valid_passes() {
            var records = List.of(Map.<String, Object>of("f", "user@example.com"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "email")))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("email type — missing @ fails")
        void email_noAt_fails() {
            var records = List.of(Map.<String, Object>of("f", "not-an-email"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "email")))).invalidRecords()).isEqualTo(1);
        }

        @Test
        @DisplayName("unknown type is treated as passing (no restriction)")
        void unknownType_passes() {
            var records = List.of(Map.<String, Object>of("f", "anything"));
            assertThat(applyAndDrain(records, params(List.of(rule("f", false, "custom_type")))).invalidRecords()).isZero();
        }
    }

    // ─── regex check ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("regex check")
    class RegexCheck {

        @Test
        @DisplayName("value matching regex — no violation")
        void matches_noViolation() {
            var r = ruleWith("code", "regex", "^[A-Z]{3}$");
            var records = List.of(Map.<String, Object>of("code", "ABC"));
            assertThat(applyAndDrain(records, params(List.of(r))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("value not matching regex — violation with rule='regex'")
        void noMatch_violation() {
            var r = ruleWith("code", "regex", "^[A-Z]{3}$");
            var records = List.of(Map.<String, Object>of("code", "abc"));
            QualityReport report = applyAndDrain(records, params(List.of(r)));
            assertThat(report.invalidRecords()).isEqualTo(1);
            assertThat(report.topViolations().get(0).rule()).isEqualTo("regex");
        }
    }

    // ─── range check ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("range check")
    class RangeCheck {

        @Test
        @DisplayName("numeric value within [min, max] — no violation")
        void numeric_withinRange_noViolation() {
            var r = ruleWith("score", "min", 0.0, "max", 100.0);
            var records = List.of(Map.<String, Object>of("score", "50"));
            assertThat(applyAndDrain(records, params(List.of(r))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("numeric value below min — violation with rule='min'")
        void numeric_belowMin_violation() {
            var r = ruleWith("score", "min", 10.0);
            var records = List.of(Map.<String, Object>of("score", "5"));
            QualityReport report = applyAndDrain(records, params(List.of(r)));
            assertThat(report.topViolations().get(0).rule()).isEqualTo("min");
        }

        @Test
        @DisplayName("numeric value above max — violation with rule='max'")
        void numeric_aboveMax_violation() {
            var r = ruleWith("score", "max", 100.0);
            var records = List.of(Map.<String, Object>of("score", "150"));
            QualityReport report = applyAndDrain(records, params(List.of(r)));
            assertThat(report.topViolations().get(0).rule()).isEqualTo("max");
        }

        @Test
        @DisplayName("string length below min — violation with rule='minLength'")
        void string_lengthBelowMin_violation() {
            var r = ruleWith("code", "min", 5.0);
            var records = List.of(Map.<String, Object>of("code", "AB"));
            QualityReport report = applyAndDrain(records, params(List.of(r)));
            assertThat(report.topViolations().get(0).rule()).isEqualTo("minLength");
        }

        @Test
        @DisplayName("string length above max — violation with rule='maxLength'")
        void string_lengthAboveMax_violation() {
            var r = ruleWith("code", "max", 3.0);
            var records = List.of(Map.<String, Object>of("code", "TOOLONG"));
            QualityReport report = applyAndDrain(records, params(List.of(r)));
            assertThat(report.topViolations().get(0).rule()).isEqualTo("maxLength");
        }
    }

    // ─── enum check ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("enum check")
    class EnumCheck {

        @Test
        @DisplayName("value in allowed set — no violation")
        void inAllowed_noViolation() {
            var r = ruleWith("status", "allowedValues", List.of("ACTIVE", "INACTIVE"));
            var records = List.of(Map.<String, Object>of("status", "ACTIVE"));
            assertThat(applyAndDrain(records, params(List.of(r))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("value not in allowed set — violation with rule='enum'")
        void notInAllowed_violation() {
            var r = ruleWith("status", "allowedValues", List.of("ACTIVE", "INACTIVE"));
            var records = List.of(Map.<String, Object>of("status", "DELETED"));
            QualityReport report = applyAndDrain(records, params(List.of(r)));
            assertThat(report.topViolations().get(0).rule()).isEqualTo("enum");
        }
    }

    // ─── cross-field check ────────────────────────────────────────────────────

    @Nested
    @DisplayName("cross-field check")
    class CrossFieldCheck {

        @Test
        @DisplayName("gt satisfied: maxPrice(100) > minPrice(50) — no violation")
        void crossField_gt_satisfied() {
            var r = ruleWith("maxPrice", "crossField", "minPrice", "crossOp", "gt");
            var records = List.of(Map.<String, Object>of("maxPrice", "100", "minPrice", "50"));
            assertThat(applyAndDrain(records, params(List.of(r))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("gt violated: maxPrice(30) > minPrice(50) — violation with rule='crossField'")
        void crossField_gt_violated() {
            var r = ruleWith("maxPrice", "crossField", "minPrice", "crossOp", "gt");
            var records = List.of(Map.<String, Object>of("maxPrice", "30", "minPrice", "50"));
            QualityReport report = applyAndDrain(records, params(List.of(r)));
            assertThat(report.topViolations().get(0).rule()).isEqualTo("crossField");
        }

        @Test
        @DisplayName("eq satisfied: a == b — no violation")
        void crossField_eq_satisfied() {
            var r = ruleWith("a", "crossField", "b", "crossOp", "eq");
            var records = List.of(Map.<String, Object>of("a", "42", "b", "42"));
            assertThat(applyAndDrain(records, params(List.of(r))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("cross field absent — no violation (nothing to compare)")
        void crossField_absent_noViolation() {
            var r = ruleWith("price", "crossField", "minPrice", "crossOp", "gte");
            var records = List.of(Map.<String, Object>of("price", "100"));
            assertThat(applyAndDrain(records, params(List.of(r))).invalidRecords()).isZero();
        }

        @Test
        @DisplayName("string cross-field: lt comparison")
        void crossField_string_lt_satisfied() {
            var r = ruleWith("start", "crossField", "end", "crossOp", "lt");
            var records = List.of(Map.<String, Object>of("start", "apple", "end", "banana"));
            assertThat(applyAndDrain(records, params(List.of(r))).invalidRecords()).isZero();
        }
    }

    // ─── quality report ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("QualityReport content")
    class ReportContent {

        @Test
        @DisplayName("all records valid → validityRate = 1.0")
        void allValid_rate100() {
            var records = List.of(
                    Map.<String, Object>of("name", "Alice"),
                    Map.<String, Object>of("name", "Bob")
            );
            QualityReport r = applyAndDrain(records, params(List.of(rule("name", true, "string"))));
            assertThat(r.totalRecords()).isEqualTo(2);
            assertThat(r.validRecords()).isEqualTo(2);
            assertThat(r.validityRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("half records invalid → validityRate = 0.5")
        void halfInvalid_rate50() {
            var records = List.of(
                    Map.<String, Object>of("name", "Alice"),
                    new HashMap<String, Object>()
            );
            QualityReport r = applyAndDrain(records, params(List.of(rule("name", true, null))));
            assertThat(r.validityRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("topViolations capped at 10 even with more violations")
        void topViolationsCappedAt10() {
            var records = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < 20; i++) records.add(new HashMap<>());
            QualityReport r = applyAndDrain(records, params(List.of(rule("name", true, null))));
            assertThat(r.topViolations()).hasSizeLessThanOrEqualTo(10);
            assertThat(r.invalidRecords()).isEqualTo(20);
        }

        @Test
        @DisplayName("drainReport() returns null after being drained twice")
        void drain_twice_returnsNull() {
            step.apply(List.of(Map.<String, Object>of("x", "1")),
                    params(List.of(rule("x", false, null))));
            ValidateFieldsStep.drainReport();
            assertThat(ValidateFieldsStep.drainReport()).isNull();
        }

        @Test
        @DisplayName("byField map contains entry for each validated field")
        void byField_containsFieldEntry() {
            var records = List.of(Map.<String, Object>of("price", "10", "name", "Alice"));
            QualityReport r = applyAndDrain(records, params(List.of(
                    rule("price", false, "number"),
                    rule("name", false, "string")
            )));
            assertThat(r.byField()).containsKeys("price", "name");
        }
    }

    // ─── multiple rules per record ────────────────────────────────────────────

    @Nested
    @DisplayName("multiple rules on same record")
    class MultipleRules {

        @Test
        @DisplayName("two violations on the same record counted as one invalid record")
        void twoViolationsSameRecord_oneInvalid() {
            var records = List.of(Map.<String, Object>of("price", "not-a-number", "score", "150"));
            var ruleList = List.of(
                    rule("price", false, "number"),
                    ruleWith("score", "max", 100.0)
            );
            QualityReport r = applyAndDrain(records, params(ruleList));
            assertThat(r.invalidRecords()).isEqualTo(1);
            assertThat(r.topViolations()).hasSize(2);
        }
    }
}