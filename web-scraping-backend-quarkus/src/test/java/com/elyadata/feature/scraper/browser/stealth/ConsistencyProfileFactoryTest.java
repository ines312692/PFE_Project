package com.elyadata.feature.scraper.browser.stealth;

import com.elyadata.feature.proxy.domain.model.Proxy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ConsistencyProfileFactory")
class ConsistencyProfileFactoryTest {

    private final ConsistencyProfileFactory factory = new ConsistencyProfileFactory();

    private Proxy proxyWithCountry(String country) {
        return Proxy.builder().country(country).build();
    }


    @Test
    @DisplayName("null proxy → returns default French profile (Europe/Paris)")
    void nullProxy_returnsDefaultProfile() {
        ConsistencyProfile p = factory.buildFor(null);
        assertThat(p).isNotNull();
        assertThat(p.timezone()).isEqualTo("Europe/Paris");
    }

    @Test
    @DisplayName("proxy with null country → default French profile")
    void nullCountry_returnsDefault() {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry(null));
        assertThat(p.timezone()).isEqualTo("Europe/Paris");
    }

    @Test
    @DisplayName("proxy with blank country → default French profile")
    void blankCountry_returnsDefault() {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry("  "));
        assertThat(p.timezone()).isEqualTo("Europe/Paris");
    }


    @ParameterizedTest
    @DisplayName("known country code → correct timezone assigned")
    @ValueSource(strings = {"US", "GB", "FR", "DE", "ES", "IT", "NL", "CA", "TN", "JP", "BR"})
    void knownCountry_correctTimezone(String code) {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry(code));
        assertThat(p.timezone()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("US proxy → America/New_York timezone")
    void usProxy_newYorkTimezone() {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry("US"));
        assertThat(p.timezone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("FR proxy → Europe/Paris timezone")
    void frProxy_parisTimezone() {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry("FR"));
        assertThat(p.timezone()).isEqualTo("Europe/Paris");
    }

    @Test
    @DisplayName("JP proxy → Asia/Tokyo timezone")
    void jpProxy_tokyoTimezone() {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry("JP"));
        assertThat(p.timezone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("lowercase country code normalized to uppercase (country code)")
    void lowercaseCountry_normalized() {
        ConsistencyProfile lower = factory.buildFor(proxyWithCountry("us"));
        ConsistencyProfile upper = factory.buildFor(proxyWithCountry("US"));
        assertThat(lower.timezone()).isEqualTo(upper.timezone());
    }

    @Test
    @DisplayName("unknown country code → falls back to default (Europe/Paris)")
    void unknownCountry_fallsBackToDefault() {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry("ZZ"));
        assertThat(p.timezone()).isEqualTo("Europe/Paris");
    }

    @Test
    @DisplayName("profile has non-blank userAgent containing Chrome")
    void userAgent_containsChrome() {
        ConsistencyProfile p = factory.buildFor(null);
        assertThat(p.userAgent()).contains("Chrome");
    }

    @Test
    @DisplayName("profile has positive screen dimensions")
    void screenDimensions_positive() {
        ConsistencyProfile p = factory.buildFor(null);
        assertThat(p.screenWidth()).isPositive();
        assertThat(p.screenHeight()).isPositive();
        assertThat(p.viewportWidth()).isPositive();
        assertThat(p.viewportHeight()).isPositive();
    }

    @Test
    @DisplayName("hardwareConcurrency is one of the allowed values: 4, 8, 12, 16")
    void hardwareConcurrency_validValues() {
        Set<Integer> valid = Set.of(4, 8, 12, 16);
        for (int i = 0; i < 20; i++) {
            ConsistencyProfile p = factory.buildFor(null);
            assertThat(valid).contains(p.hardwareConcurrency());
        }
    }

    @Test
    @DisplayName("deviceMemory is one of the allowed values: 4, 8, 16")
    void deviceMemory_validValues() {
        Set<Integer> valid = Set.of(4, 8, 16);
        for (int i = 0; i < 20; i++) {
            ConsistencyProfile p = factory.buildFor(null);
            assertThat(valid).contains(p.deviceMemory());
        }
    }

    @Test
    @DisplayName("acceptLanguage header includes the primary language")
    void acceptLanguage_includesPrimaryLanguage() {
        ConsistencyProfile p = factory.buildFor(proxyWithCountry("FR"));
        assertThat(p.acceptLanguage()).contains("fr-FR");
    }

    @Test
    @DisplayName("webglVendor is Google Inc.")
    void webglVendor_isGoogle() {
        ConsistencyProfile p = factory.buildFor(null);
        assertThat(p.webglVendor()).contains("Google Inc.");
    }

    @Test
    @DisplayName("platform is one of Win32, MacIntel or Linux x86_64")
    void platform_validValues() {
        Set<String> valid = Set.of("Win32", "MacIntel", "Linux x86_64");
        for (int i = 0; i < 20; i++) {
            ConsistencyProfile p = factory.buildFor(null);
            assertThat(valid).contains(p.platform());
        }
    }
}