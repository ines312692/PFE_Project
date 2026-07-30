package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PiiAnonymizationStep")
class PiiAnonymizationStepTest {

    private PiiAnonymizationStep step;

    @BeforeEach
    void setUp() {
        step = new PiiAnonymizationStep();
    }

    @Test
    @DisplayName("type() returns ANONYMIZE_PII")
    void type_returnsAnonymizePii() {
        assertThat(step.type()).isEqualTo(EtlStepType.ANONYMIZE_PII);
    }


    @Test
    @DisplayName("empty records list → empty list returned")
    void emptyList_returnsEmpty() {
        assertThat(step.apply(List.of(), Map.of())).isEmpty();
    }

    @Nested
    @DisplayName("EMAIL detection")
    class EmailDetection {

        @Test
        @DisplayName("email address in field is masked")
        void emailMasked() {
            var records = List.of(Map.<String, Object>of("contact", "user@example.com"));
            var result = step.apply(records, Map.of("categories", List.of("EMAIL")));

            assertThat(result.getFirst().get("contact").toString())
                    .doesNotContain("user@example.com");
        }

        @Test
        @DisplayName("FULL mask format replaces email with ***")
        void fullMaskFormat() {
            var records = List.of(Map.<String, Object>of("email", "alice@test.com"));
            var result = step.apply(records,
                    Map.of("categories", List.of("EMAIL"), "maskFormat", "FULL"));

            assertThat(result.getFirst().get("email")).isEqualTo("***");
        }

        @Test
        @DisplayName("HASH mask format wraps email in [anon:xxxx]")
        void hashMaskFormat() {
            var records = List.of(Map.<String, Object>of("email", "alice@test.com"));
            var result = step.apply(records,
                    Map.of("categories", List.of("EMAIL"), "maskFormat", "HASH"));

            String value = result.getFirst().get("email").toString();
            assertThat(value).startsWith("[anon:").endsWith("]");
        }

        @Test
        @DisplayName("PARTIAL mask format keeps first and last character")
        void partialMaskFormat() {
            var records = List.of(Map.<String, Object>of("email", "alice@test.com"));
            var result = step.apply(records,
                    Map.of("categories", List.of("EMAIL"), "maskFormat", "PARTIAL"));

            String value = result.getFirst().get("email").toString();
            assertThat(value).startsWith("a").endsWith("m");
            assertThat(value).contains("***");
        }
    }


    @Nested
    @DisplayName("PHONE detection")
    class PhoneDetection {

        @Test
        @DisplayName("French mobile number is masked")
        void frenchMobileNumber() {
            var records = List.of(Map.<String, Object>of("tel", "06 12 34 56 78"));
            var result = step.apply(records, Map.of("categories", List.of("PHONE")));

            assertThat(result.getFirst().get("tel").toString()).doesNotContain("06 12 34 56 78");
        }

        @Test
        @DisplayName("international phone number is masked")
        void internationalPhone() {
            var records = List.of(Map.<String, Object>of("phone", "+33 6 12 34 56 78"));
            var result = step.apply(records, Map.of("categories", List.of("PHONE")));

            assertThat(result.getFirst().get("phone").toString()).doesNotContain("+33 6 12 34 56 78");
        }
    }


    @Nested
    @DisplayName("CREDIT_CARD detection")
    class CreditCardDetection {

        @Test
        @DisplayName("credit card number is masked")
        void creditCardMasked() {
            var records = List.of(Map.<String, Object>of("card", "4532 1234 5678 9012"));
            var result = step.apply(records, Map.of("categories", List.of("CREDIT_CARD")));

            assertThat(result.getFirst().get("card").toString())
                    .doesNotContain("4532 1234 5678 9012");
        }
    }


    @Nested
    @DisplayName("IPV4 detection")
    class Ipv4Detection {

        @Test
        @DisplayName("IPv4 address is masked")
        void ipv4Masked() {
            var records = List.of(Map.<String, Object>of("source_ip", "192.168.1.100"));
            var result = step.apply(records, Map.of("categories", List.of("IPV4")));

            assertThat(result.getFirst().get("source_ip").toString())
                    .doesNotContain("192.168.1.100");
        }
    }


    @Nested
    @DisplayName("field filtering")
    class FieldFiltering {

        @Test
        @DisplayName("'fields' param restricts scanning to specified fields only")
        void fieldsParam_restrictsToSpecifiedFields() {
            var records = List.of(Map.<String, Object>of(
                    "email1", "alice@example.com",
                    "email2", "bob@example.com"
            ));
            var result = step.apply(records,
                    Map.of("categories", List.of("EMAIL"), "fields", List.of("email1")));

            assertThat(result.getFirst().get("email1").toString()).doesNotContain("alice@example.com");
            assertThat(result.getFirst().get("email2")).isEqualTo("bob@example.com");
        }

        @Test
        @DisplayName("'skipFields' param excludes those fields from scanning")
        void skipFieldsParam_excludesFromScanning() {
            var records = List.of(Map.<String, Object>of(
                    "email", "alice@example.com",
                    "public_email", "contact@company.com"
            ));
            var result = step.apply(records,
                    Map.of("categories", List.of("EMAIL"), "skipFields", List.of("public_email")));

            assertThat(result.getFirst().get("email").toString()).doesNotContain("alice@example.com");
            assertThat(result.getFirst().get("public_email")).isEqualTo("contact@company.com");
        }
    }


    @Nested
    @DisplayName("category filtering")
    class CategoryFiltering {

        @Test
        @DisplayName("no categories param → all patterns applied (email masked)")
        void noCategories_allPatternsApplied() {
            var records = List.of(Map.<String, Object>of("data", "contact: alice@test.org"));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst().get("data").toString()).doesNotContain("alice@test.org");
        }

        @Test
        @DisplayName("unrelated category → email in field not masked")
        void unrelatedCategory_emailNotMasked() {
            var records = List.of(Map.<String, Object>of("email", "alice@test.com"));
            var result = step.apply(records, Map.of("categories", List.of("IPV4")));

            assertThat(result.getFirst().get("email")).isEqualTo("alice@test.com");
        }

        @Test
        @DisplayName("unknown category name ignored silently")
        void unknownCategory_ignored() {
            var records = List.of(Map.<String, Object>of("f", "some text"));
            var result = step.apply(records, Map.of("categories", List.of("UNKNOWN_CATEGORY")));

            assertThat(result.getFirst().get("f")).isEqualTo("some text");
        }
    }


    @Test
    @DisplayName("non-string field values are passed through untouched")
    void nonStringValue_passedThrough() {
        var records = List.of(Map.<String, Object>of("count", 42, "flag", true));
        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("count")).isEqualTo(42);
        assertThat(result.getFirst().get("flag")).isEqualTo(true);
    }


    @Test
    @DisplayName("each record independently anonymized")
    void multipleRecords_eachAnonymized() {
        var records = List.of(
                Map.<String, Object>of("email", "a@example.com"),
                Map.<String, Object>of("email", "b@example.com")
        );
        var result = step.apply(records, Map.of("categories", List.of("EMAIL")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("email").toString()).doesNotContain("a@example.com");
        assertThat(result.get(1).get("email").toString()).doesNotContain("b@example.com");
    }
}