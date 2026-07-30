package com.elyadata.common.llm;

public interface LlmChatClient {

    String complete(String systemPrompt, String userMessage);

    LlmResult completeWithUsage(String systemPrompt, String userMessage, LlmCallOptions options);

    default LlmResult completeWithUsage(String systemPrompt, String userMessage) {
        return completeWithUsage(systemPrompt, userMessage, LlmCallOptions.defaults());
    }

    boolean isAvailable();
}