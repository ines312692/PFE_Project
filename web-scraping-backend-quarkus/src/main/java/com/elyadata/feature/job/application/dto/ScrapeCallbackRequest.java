package com.elyadata.feature.job.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

@Builder
@Jacksonized
public record ScrapeCallbackRequest(

        @NotBlank
        String status,

        List<Map<String, Object>> records,

        List<String> errors,

        List<String> logs,

        @JsonProperty("pages_crawled")
        int pagesCrawled,

        @JsonProperty("duration_ms")
        int durationMs,

        @JsonProperty("engine_used")
        String engineUsed,
        @JsonProperty("raw_content")
        String rawContent
) {
}