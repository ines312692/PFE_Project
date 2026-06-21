package com.elyadata.common.llm;


public interface LlmChatClient {

    String complete(String systemPrompt, String userMessage);

    boolean isAvailable();
}