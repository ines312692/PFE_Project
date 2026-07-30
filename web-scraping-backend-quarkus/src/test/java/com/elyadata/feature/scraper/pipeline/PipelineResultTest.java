package com.elyadata.feature.scraper.pipeline;

import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PipelineResult")
class PipelineResultTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFields_accessible() {
        List<Map<String, Object>> records = List.of(Map.of("k", "v"));
        List<String> errors = List.of("err1");
        List<String> logs = List.of("[INFO] done");
        List<DiscoveredFile> discovered = List.of();

        PipelineResult result = new PipelineResult(
                "completed", records, errors, logs,
                5, 1200L, "playwright", "raw html", discovered);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.records()).isSameAs(records);
        assertThat(result.errors()).isSameAs(errors);
        assertThat(result.logs()).isSameAs(logs);
        assertThat(result.pagesCrawled()).isEqualTo(5);
        assertThat(result.durationMs()).isEqualTo(1200L);
        assertThat(result.engineUsed()).isEqualTo("playwright");
        assertThat(result.rawContent()).isEqualTo("raw html");
        assertThat(result.discoveredFiles()).isSameAs(discovered);
    }

    @Test
    @DisplayName("status=failed is stored as-is")
    void failedStatus_storedAsIs() {
        PipelineResult result = new PipelineResult(
                "failed", List.of(), List.of("err"), List.of(),
                0, 0L, "playwright", null, List.of());

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.rawContent()).isNull();
    }


    @Test
    @DisplayName("two records with identical fields are equal")
    void equality_identicalFields() {
        List<Map<String, Object>> records = List.of(Map.of("a", "1"));

        PipelineResult r1 = new PipelineResult(
                "completed", records, List.of(), List.of(),
                1, 500L, "selenium", null, List.of());

        PipelineResult r2 = new PipelineResult(
                "completed", records, List.of(), List.of(),
                1, 500L, "selenium", null, List.of());

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("two records with different status are not equal")
    void equality_differentStatus_notEqual() {
        PipelineResult completed = new PipelineResult(
                "completed", List.of(), List.of(), List.of(),
                1, 500L, "playwright", null, List.of());

        PipelineResult failed = new PipelineResult(
                "failed", List.of(), List.of(), List.of(),
                1, 500L, "playwright", null, List.of());

        assertThat(completed).isNotEqualTo(failed);
    }


    @Test
    @DisplayName("zero pages crawled and empty collections are valid")
    void emptyResult_isValid() {
        PipelineResult result = new PipelineResult(
                "failed", List.of(), List.of(), List.of(),
                0, 0L, "playwright", null, List.of());

        assertThat(result.pagesCrawled()).isZero();
        assertThat(result.durationMs()).isZero();
        assertThat(result.records()).isEmpty();
        assertThat(result.errors()).isEmpty();
        assertThat(result.discoveredFiles()).isEmpty();
    }

    @Test
    @DisplayName("rawContent can be null")
    void rawContent_canBeNull() {
        PipelineResult result = new PipelineResult(
                "completed", List.of(Map.of("k", "v")), List.of(), List.of(),
                1, 100L, "playwright", null, List.of());

        assertThat(result.rawContent()).isNull();
    }

    @Test
    @DisplayName("rawContent can be a multi-page string")
    void rawContent_multiPage() {
        String raw = "=== PAGE: https://example.com ===\npage1\n\n=== PAGE: https://example.com/p2 ===\npage2";
        PipelineResult result = new PipelineResult(
                "completed", List.of(), List.of(), List.of(),
                2, 800L, "playwright", raw, List.of());

        assertThat(result.rawContent()).contains("=== PAGE:");
        assertThat(result.rawContent()).contains("page1");
        assertThat(result.rawContent()).contains("page2");
    }
}
