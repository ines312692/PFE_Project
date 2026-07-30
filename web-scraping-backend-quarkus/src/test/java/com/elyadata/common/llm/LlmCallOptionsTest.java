package com.elyadata.common.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmCallOptions")
class LlmCallOptionsTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("null timeout falls back to the 60s default")
        void nullTimeout_usesDefault() {
            LlmCallOptions opts = new LlmCallOptions("model-x", 0.5, null);

            assertThat(opts.timeout()).isEqualTo(Duration.ofSeconds(60));
        }

        @Test
        @DisplayName("defaults() has no model, temperature 0.0 and 60s timeout")
        void defaults_hasExpectedShape() {
            LlmCallOptions opts = LlmCallOptions.defaults();

            assertThat(opts.model()).isNull();
            assertThat(opts.temperature()).isZero();
            assertThat(opts.timeout()).isEqualTo(Duration.ofSeconds(60));
        }

        @Test
        @DisplayName("of(model, temp, Duration) keeps the given values")
        void of_withDuration() {
            LlmCallOptions opts = LlmCallOptions.of("model-y", 0.2, Duration.ofSeconds(10));

            assertThat(opts.model()).isEqualTo("model-y");
            assertThat(opts.temperature()).isEqualTo(0.2);
            assertThat(opts.timeout()).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("of(model, temp, seconds) converts seconds to a Duration")
        void of_withIntSeconds() {
            LlmCallOptions opts = LlmCallOptions.of("model-z", 0.1, 30);

            assertThat(opts.timeout()).isEqualTo(Duration.ofSeconds(30));
        }
    }

    @Nested
    @DisplayName("cacheKey()")
    class CacheKey {

        @Test
        @DisplayName("uses the explicit model when present")
        void explicitModel_usedInKey() {
            LlmCallOptions opts = LlmCallOptions.of("explicit-model", 0.3, 15);

            assertThat(opts.cacheKey("fallback")).isEqualTo("explicit-model|0.3|15");
        }

        @Test
        @DisplayName("falls back to the fallback model when null")
        void nullModel_usesFallback() {
            LlmCallOptions opts = new LlmCallOptions(null, 0.0, Duration.ofSeconds(5));

            assertThat(opts.cacheKey("fallback-model")).isEqualTo("fallback-model|0.0|5");
        }

        @Test
        @DisplayName("falls back to the fallback model when blank")
        void blankModel_usesFallback() {
            LlmCallOptions opts = new LlmCallOptions("   ", 0.0, Duration.ofSeconds(5));

            assertThat(opts.cacheKey("fallback-model")).isEqualTo("fallback-model|0.0|5");
        }
    }

    @Nested
    @DisplayName("resolveModel()")
    class ResolveModel {

        @Test
        @DisplayName("returns the explicit model when present")
        void explicitModel_returned() {
            LlmCallOptions opts = LlmCallOptions.of("explicit", 0.0, 5);

            assertThat(opts.resolveModel("fallback")).isEqualTo("explicit");
        }

        @Test
        @DisplayName("returns the fallback when model is null")
        void nullModel_returnsFallback() {
            LlmCallOptions opts = new LlmCallOptions(null, 0.0, Duration.ofSeconds(5));

            assertThat(opts.resolveModel("fallback")).isEqualTo("fallback");
        }

        @Test
        @DisplayName("returns the fallback when model is blank")
        void blankModel_returnsFallback() {
            LlmCallOptions opts = new LlmCallOptions("  ", 0.0, Duration.ofSeconds(5));

            assertThat(opts.resolveModel("fallback")).isEqualTo("fallback");
        }
    }
}
