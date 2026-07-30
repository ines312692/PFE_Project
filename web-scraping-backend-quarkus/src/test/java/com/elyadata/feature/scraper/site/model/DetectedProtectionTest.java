package com.elyadata.feature.scraper.site.model;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class DetectedProtectionTest {

    @Test
    void constructsValidProtection() {
        DetectedProtection p = new DetectedProtection(
                AntiBotProvider.CLOUDFLARE, 0.95, "header present");

        assertThat(p.provider()).isEqualTo(AntiBotProvider.CLOUDFLARE);
        assertThat(p.confidence()).isEqualTo(0.95);
        assertThat(p.evidence()).isEqualTo("header present");
    }

    @Test
    void acceptsBoundaryConfidences() {
        assertThatCode(() -> new DetectedProtection(AntiBotProvider.CLOUDFLARE, 0.0, "x"))
                .doesNotThrowAnyException();
        assertThatCode(() -> new DetectedProtection(AntiBotProvider.CLOUDFLARE, 1.0, "x"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, -1.0, 2.0})
    void rejectsConfidenceOutOfRange(double confidence) {
        assertThatThrownBy(() ->
                new DetectedProtection(AntiBotProvider.CLOUDFLARE, confidence, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejectsNullProvider() {
        assertThatThrownBy(() -> new DetectedProtection(null, 0.5, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankEvidence(String evidence) {
        assertThatThrownBy(() ->
                new DetectedProtection(AntiBotProvider.CLOUDFLARE, 0.5, evidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence");
    }
}
