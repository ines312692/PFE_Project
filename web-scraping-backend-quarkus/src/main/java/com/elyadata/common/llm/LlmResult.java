package com.elyadata.common.llm;

import dev.langchain4j.model.output.TokenUsage;


public record LlmResult(String text, TokenUsage usage, String model) {

    public Integer promptTokens() {
        return usage != null ? usage.inputTokenCount() : null;
    }

    public Integer completionTokens() {
        return usage != null ? usage.outputTokenCount() : null;
    }

    public Integer totalTokens() {
        return usage != null ? usage.totalTokenCount() : null;
    }

    public String textOrEmpty() {
        return text == null ? "" : text;
    }
}