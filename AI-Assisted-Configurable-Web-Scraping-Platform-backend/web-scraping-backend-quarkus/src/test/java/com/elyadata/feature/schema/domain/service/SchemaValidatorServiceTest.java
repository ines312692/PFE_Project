package com.elyadata.feature.schema.domain.service;

import com.elyadata.feature.schema.application.dto.SchemaValidationRequest;
import com.elyadata.feature.schema.application.dto.SchemaValidationRequest.FieldToValidate;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse.FieldStatus;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse.FieldValidationResult;
import com.elyadata.feature.schema.domain.model.SchemaFieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidatorServiceTest {

    @Test
    void errorResponse_isUnsuccessfulWithMessage() {
        SchemaValidationResponse r = SchemaValidationResponse.error("bad url");

        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).isEqualTo("bad url");
        assertThat(r.results()).isEmpty();
    }


    @Test
    void fieldValidationResult_okBuildsExpectedShape() {
        FieldValidationResult r = FieldValidationResult.builder()
                .fieldName("price")
                .currentSelector(".price")
                .status(FieldStatus.OK)
                .matchCount(3)
                .previewValues(List.of("10", "20", "30"))
                .build();

        assertThat(r.status()).isEqualTo(FieldStatus.OK);
        assertThat(r.matchCount()).isEqualTo(3);
        assertThat(r.previewValues()).containsExactly("10", "20", "30");
    }

    @Test
    void fieldValidationResult_brokenCarriesReason() {
        FieldValidationResult r = FieldValidationResult.builder()
                .fieldName("title")
                .currentSelector(".missing")
                .status(FieldStatus.BROKEN)
                .matchCount(0)
                .errorReason("Selector matches 0 elements on the page")
                .previewValues(List.of())
                .build();

        assertThat(r.status()).isEqualTo(FieldStatus.BROKEN);
        assertThat(r.errorReason()).contains("0 elements");
    }

    @Test
    void request_holdsFieldsToValidate() {
        FieldToValidate f = new FieldToValidate(
                "price", ".price", SchemaFieldType.TEXT, null);
        SchemaValidationRequest req = new SchemaValidationRequest(
                "https://example.com", List.of(f));

        assertThat(req.targetUrl()).isEqualTo("https://example.com");
        assertThat(req.fields()).hasSize(1);
        assertThat(req.fields().get(0).type()).isEqualTo(SchemaFieldType.TEXT);
    }


    @Test
    void errorResponse_zeroesCounts() {
        SchemaValidationResponse r = SchemaValidationResponse.error("oops");

        assertThat(r.totalFields()).isZero();
        assertThat(r.validFields()).isZero();
        assertThat(r.brokenFields()).isZero();
    }
}