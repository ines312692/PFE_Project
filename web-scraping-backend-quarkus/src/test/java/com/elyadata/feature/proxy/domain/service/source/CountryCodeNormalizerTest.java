package com.elyadata.feature.proxy.domain.service.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CountryCodeNormalizerTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void normalize_returnsNullForNullOrBlank(String raw) {
        assertThat(CountryCodeNormalizer.normalize(raw)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "us, US",
            "Us, US",
            "fr, FR",
            "DE, DE"
    })
    void normalize_uppercasesTwoLetterCodes(String raw, String expected) {
        assertThat(CountryCodeNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void normalize_trimsSurroundingWhitespace() {
        assertThat(CountryCodeNormalizer.normalize("  fr  ")).isEqualTo("FR");
    }

    @ParameterizedTest
    @CsvSource({
            "USA, US",
            "usa, US",
            "GBR, GB",
            "FRA, FR",
            "DEU, DE",
            "JPN, JP",
            "BRA, BR"
    })
    void normalize_mapsIso3ToIso2(String raw, String expected) {
        assertThat(CountryCodeNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "united states, US",
            "United States, US",
            "united states of america, US",
            "united kingdom, GB",
            "great britain, GB",
            "germany, DE",
            "south korea, KR",
            "korea, KR",
            "russian federation, RU",
            "hong kong, HK"
    })
    void normalize_mapsCountryNamesToIso2(String raw, String expected) {
        assertThat(CountryCodeNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ZZ",              // valid format but not a real mapping → still returned as-is (uppercased)
    })
    void normalize_returnsUnknownTwoLetterAsIs(String raw) {
        // Any 2-letter alpha string is accepted and uppercased, regardless of validity
        assertThat(CountryCodeNormalizer.normalize(raw)).isEqualTo("ZZ");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "XYZ",
            "atlantis",
            "12",
            "u"
    })
    void normalize_returnsNullForUnknownInput(String raw) {
        assertThat(CountryCodeNormalizer.normalize(raw)).isNull();
    }

    @Test
    void normalizeAll_returnsEmptyForNull() {
        assertThat(CountryCodeNormalizer.normalizeAll(null)).isEmpty();
    }

    @Test
    void normalizeAll_returnsEmptyForEmptyList() {
        assertThat(CountryCodeNormalizer.normalizeAll(List.of())).isEmpty();
    }

    @Test
    void normalizeAll_normalizesFiltersNullsAndDeduplicates() {
        List<String> input = Arrays.asList("us", "USA", "France", "xyz", "fr");

        List<String> result = CountryCodeNormalizer.normalizeAll(input);
        assertThat(result).containsExactly("US", "FR");
    }
}
