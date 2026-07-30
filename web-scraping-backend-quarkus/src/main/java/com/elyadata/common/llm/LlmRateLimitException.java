package com.elyadata.common.llm;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import lombok.Getter;

import java.time.Duration;
import java.util.Set;

@Getter
public class LlmRateLimitException extends Infrastructure5xxException {

    private final transient Duration retryAfter;
    private final String provider;

    public LlmRateLimitException(String provider, String message, Duration retryAfter) {
        super(message, null, Set.of());
        this.provider = provider;
        this.retryAfter = retryAfter;
    }
}