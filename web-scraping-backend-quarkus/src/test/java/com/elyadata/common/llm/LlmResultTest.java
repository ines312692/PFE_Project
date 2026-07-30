package com.elyadata.common.llm;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmResult")
class LlmResultTest {

    @Test
    @DisplayName("exposes prompt/completion/total tokens from the usage")
    void withUsage_exposesTokenCounts() {
        LlmResult result = new LlmResult("hello", new TokenUsage(10, 20, 30), "model-x");

        assertThat(result.promptTokens()).isEqualTo(10);
        assertThat(result.completionTokens()).isEqualTo(20);
        assertThat(result.totalTokens()).isEqualTo(30);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.model()).isEqualTo("model-x");
    }

    @Test
    @DisplayName("token accessors return null when usage is null")
    void nullUsage_returnsNullTokenCounts() {
        LlmResult result = new LlmResult("hello", null, "model-x");

        assertThat(result.promptTokens()).isNull();
        assertThat(result.completionTokens()).isNull();
        assertThat(result.totalTokens()).isNull();
    }

    @Test
    @DisplayName("textOrEmpty() returns the text when present")
    void textOrEmpty_returnsText() {
        LlmResult result = new LlmResult("hello", null, "model-x");

        assertThat(result.textOrEmpty()).isEqualTo("hello");
    }

    @Test
    @DisplayName("textOrEmpty() returns empty string when text is null")
    void textOrEmpty_nullText_returnsEmptyString() {
        LlmResult result = new LlmResult(null, null, "model-x");

        assertThat(result.textOrEmpty()).isEmpty();
    }
}