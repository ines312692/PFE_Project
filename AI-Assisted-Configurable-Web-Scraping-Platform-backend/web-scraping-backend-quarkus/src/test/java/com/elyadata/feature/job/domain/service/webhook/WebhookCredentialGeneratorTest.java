package com.elyadata.feature.job.domain.service.webhook;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookCredentialGenerator")
class WebhookCredentialGeneratorTest {

    private final WebhookCredentialGenerator generator = new WebhookCredentialGenerator();

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("returns non-null credentials with non-blank token and secret")
        void nonNullCredentials() {
            var creds = generator.generate();
            assertThat(creds).isNotNull();
            assertThat(creds.token()).isNotBlank();
            assertThat(creds.secret()).isNotBlank();
        }

        @Test
        @DisplayName("enabled is true on freshly generated credentials")
        void enabledIsTrue() {
            assertThat(generator.generate().enabled()).isTrue();
        }

        @Test
        @DisplayName("token is URL-safe base64 (no +, /, =)")
        void tokenIsUrlSafeBase64() {
            String token = generator.generate().token();
            assertThat(token).doesNotContain("+", "/", "=");
        }

        @Test
        @DisplayName("secret is longer than token (more entropy)")
        void secretLongerThanToken() {
            var creds = generator.generate();
            assertThat(creds.secret().length()).isGreaterThanOrEqualTo(creds.token().length());
        }

        @Test
        @DisplayName("each call produces a different token")
        void tokensAreUnique() {
            String t1 = generator.generate().token();
            String t2 = generator.generate().token();
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("each call produces a different secret")
        void secretsAreUnique() {
            String s1 = generator.generate().secret();
            String s2 = generator.generate().secret();
            assertThat(s1).isNotEqualTo(s2);
        }
    }

    @Nested
    @DisplayName("regenerateSecret")
    class RegenerateSecret {

        @Test
        @DisplayName("preserves the existing token")
        void preservesToken() {
            String token = generator.generate().token();
            var renewed = generator.regenerateSecret(token);
            assertThat(renewed.token()).isEqualTo(token);
        }

        @Test
        @DisplayName("generates a new secret different from the original")
        void newSecretIsDifferent() {
            var original = generator.generate();
            var renewed = generator.regenerateSecret(original.token());
            assertThat(renewed.secret()).isNotEqualTo(original.secret());
        }

        @Test
        @DisplayName("enabled is true after regeneration")
        void enabledAfterRegenerate() {
            String token = generator.generate().token();
            assertThat(generator.regenerateSecret(token).enabled()).isTrue();
        }
    }
}
