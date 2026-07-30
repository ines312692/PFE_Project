package com.elyadata.feature.audit.infrastructure.interceptor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SensitiveDataMasker")
class SensitiveDataMaskerTest {


    @Test
    @DisplayName("null body → returned as-is")
    void null_returnedAsIs() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
    }

    @Test
    @DisplayName("blank body → returned as-is")
    void blank_returnedAsIs() {
        assertThat(SensitiveDataMasker.mask("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("empty string → returned as-is")
    void empty_returnedAsIs() {
        assertThat(SensitiveDataMasker.mask("")).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("field is masked to ***")
    @ValueSource(strings = {
            "password", "currentPassword", "newPassword", "secret",
            "clientSecret", "webhookSecret", "apiKey", "api_key",
            "token", "accessToken", "refreshToken", "authorization", "bearer"
    })
    void sensitiveField_isMasked(String field) {
        String body = "{\"" + field + "\":\"super-secret-value\"}";
        String result = SensitiveDataMasker.mask(body);

        assertThat(result).doesNotContain("super-secret-value");
        assertThat(result).contains("***");
        assertThat(result).contains("\"" + field + "\"");
    }

    @Test
    @DisplayName("password masked regardless of case (CASE_INSENSITIVE)")
    void caseInsensitive_PASSWORD() {
        String body = "{\"PASSWORD\":\"top-secret\"}";
        String result = SensitiveDataMasker.mask(body);

        assertThat(result).doesNotContain("top-secret");
        assertThat(result).contains("***");
    }


    @Test
    @DisplayName("non-sensitive fields are left unchanged")
    void nonSensitiveField_unchanged() {
        String body = "{\"username\":\"alice\",\"email\":\"alice@example.com\"}";
        String result = SensitiveDataMasker.mask(body);

        assertThat(result).isEqualTo(body);
    }


    @Test
    @DisplayName("mixed body — sensitive masked, public visible")
    void mixedBody_sensitiveOnlyMasked() {
        String body = "{\"username\":\"alice\",\"password\":\"secret123\",\"email\":\"alice@x.com\"}";
        String result = SensitiveDataMasker.mask(body);

        assertThat(result).contains("alice");
        assertThat(result).contains("alice@x.com");
        assertThat(result).doesNotContain("secret123");
        assertThat(result).contains("***");
    }

    @Test
    @DisplayName("multiple sensitive fields — all masked")
    void multipleSensitiveFields_allMasked() {
        String body = "{\"password\":\"p1\",\"token\":\"t1\",\"name\":\"Bob\"}";
        String result = SensitiveDataMasker.mask(body);

        assertThat(result).doesNotContain("\"p1\"");
        assertThat(result).doesNotContain("\"t1\"");
        assertThat(result).contains("Bob");
    }


    @Test
    @DisplayName("plain text body without JSON — returned unchanged")
    void plainText_unchanged() {
        String body = "just some plain text without any json";
        assertThat(SensitiveDataMasker.mask(body)).isEqualTo(body);
    }
}