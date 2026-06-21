package com.elyadata.common.llm;

public interface VisionLlmClient {
    String analyzeImage(String prompt, String imageBase64, String mimeType);

    boolean isAvailable();
}