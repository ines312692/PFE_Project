package com.elyadata.feature.etl;

import com.elyadata.feature.etl.domain.model.EtlConfig;
import com.elyadata.feature.etl.domain.model.EtlStepConfig;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import com.elyadata.feature.etl.step.*;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EtlPipelineRunner")
class EtlPipelineRunnerTest {

    private EtlPipelineRunner runner;

    @BeforeEach
    void setUp() {
        EtlStepRegistry registry = registryOf(
                new HtmlStripperStep(),
                new TrimWhitespaceStep(),
                new MissingValueHandlerStep(),
                new DeduplicateStep(),
                new DateNormalizerStep(),
                new PriceNormalizerStep(),
                new CapitalizeStep(),
                new EnumNormalizerStep()
        );
        runner = new EtlPipelineRunner(registry);
    }

    @Nested
    @DisplayName("noOp path — config has no steps")
    class NoOpPath {

        @Test
        @DisplayName("returns all raw records unchanged when config has no steps")
        void run_emptyConfig_returnsRawRecords() {
            var records = List.of(Map.<String, Object>of("name", "  Alice  "));
            var result = runner.run(records, EtlConfig.empty());

            assertThat(result.records()).isEqualTo(records);
            assertThat(result.stepReports()).isEmpty();
            assertThat(result.removedCount()).isZero();
            assertThat(result.durationMs()).isZero();
        }

        @Test
        @DisplayName("rawCount equals cleanCount when no steps run")
        void run_emptyConfig_countsAreEqual() {
            var records = List.of(Map.<String, Object>of("a", "1"), Map.<String, Object>of("a", "2"));
            var result = runner.run(records, EtlConfig.empty());

            assertThat(result.rawCount()).isEqualTo(2);
            assertThat(result.cleanCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("single step execution")
    class SingleStep {

        @Test
        @DisplayName("TRIM_WHITESPACE step strips string values")
        void run_trimStep_stripsValues() {
            var records = List.of(Map.<String, Object>of("name", "  Alice  "));
            var config = configOf(new EtlStepConfig(EtlStepType.TRIM_WHITESPACE, Map.of()));

            var result = runner.run(records, config);

            assertThat(result.records().getFirst().get("name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("STRIP_HTML step removes tags")
        void run_htmlStep_removesTags() {
            var records = List.of(Map.<String, Object>of("body", "<p>Hello</p>"));
            var config = configOf(new EtlStepConfig(EtlStepType.STRIP_HTML, Map.of()));

            var result = runner.run(records, config);

            assertThat(result.records().getFirst().get("body")).isEqualTo("Hello");
        }

        @Test
        @DisplayName("DEDUPLICATE step removes duplicate records")
        void run_deduplicateStep_removesduplicates() {
            Map<String, Object> rec = Map.of("id", "1", "name", "Alice");
            var records = new ArrayList<>(List.of(rec, rec,
                    Map.<String, Object>of("id", "2", "name", "Bob")));
            var config = configOf(new EtlStepConfig(
                    EtlStepType.DEDUPLICATE, Map.of("keys", List.of("id"))));

            var result = runner.run(records, config);

            assertThat(result.cleanCount()).isEqualTo(2);
            assertThat(result.removedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("produces a StepReport with success=true on normal execution")
        void run_stepReport_successTrue() {
            var records = List.of(Map.<String, Object>of("v", "hello"));
            var config = configOf(new EtlStepConfig(EtlStepType.TRIM_WHITESPACE, Map.of()));

            var result = runner.run(records, config);

            assertThat(result.stepReports()).hasSize(1);
            var report = result.stepReports().getFirst();
            assertThat(report.success()).isTrue();
            assertThat(report.error()).isNull();
            assertThat(report.step()).isEqualTo("TRIM_WHITESPACE");
            assertThat(report.inputCount()).isEqualTo(1);
            assertThat(report.outputCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("chained pipeline")
    class ChainedPipeline {

        @Test
        @DisplayName("applies steps in declared order")
        void run_chainedSteps_appliedInOrder() {
            var records = List.of(Map.<String, Object>of("text", "  <b>Hello</b>  "));
            var config = new EtlConfig(List.of(
                    new EtlStepConfig(EtlStepType.STRIP_HTML, Map.of()),
                    new EtlStepConfig(EtlStepType.TRIM_WHITESPACE, Map.of())
            ));

            var result = runner.run(records, config);

            assertThat(result.records().getFirst().get("text")).isEqualTo("Hello");
            assertThat(result.stepReports()).hasSize(2);
        }

        @Test
        @DisplayName("trim + capitalize pipeline produces expected output")
        void run_trimAndCapitalize() {
            var records = List.of(Map.<String, Object>of("name", "  alice smith  "));
            var config = new EtlConfig(List.of(
                    new EtlStepConfig(EtlStepType.TRIM_WHITESPACE, Map.of()),
                    new EtlStepConfig(EtlStepType.CAPITALIZE, Map.of("mode", "title"))
            ));

            var result = runner.run(records, config);

            assertThat(result.records().getFirst().get("name")).isEqualTo("Alice Smith");
        }

        @Test
        @DisplayName("missing-value-fill + deduplicate pipeline reduces records correctly")
        void run_fillThenDeduplicate() {
            Map<String, Object> r1 = new HashMap<>();
            r1.put("id", null);
            r1.put("val", "a");
            Map<String, Object> r2 = new HashMap<>();
            r2.put("id", null);
            r2.put("val", "b");
            var records = List.of(r1, r2);

            var config = new EtlConfig(List.of(
                    new EtlStepConfig(EtlStepType.MISSING_VALUE_HANDLER,
                            Map.of("strategy", "fill", "fallback", "N/A")),
                    new EtlStepConfig(EtlStepType.DEDUPLICATE,
                            Map.of("keys", List.of("id")))
            ));

            var result = runner.run(records, config);
            assertThat(result.cleanCount()).isEqualTo(1);
            assertThat(result.removedCount()).isEqualTo(1);
        }
    }


    @Nested
    @DisplayName("error resilience")
    class ErrorResilience {

        @Test
        @DisplayName("continues pipeline when a step throws — records unchanged for that step")
        void run_stepThrows_continuesPipeline() {
            EtlStepRegistry brokenRegistry = registryOf(
                    new HtmlStripperStep(),
                    new TrimWhitespaceStep() {
                        @Override
                        public List<Map<String, Object>> apply(
                                List<Map<String, Object>> records, Map<String, Object> params) {
                            throw new RuntimeException("simulated failure");
                        }
                    },
                    new MissingValueHandlerStep(),
                    new DeduplicateStep(),
                    new DateNormalizerStep(),
                    new PriceNormalizerStep(),
                    new CapitalizeStep(),
                    new EnumNormalizerStep()
            );
            EtlPipelineRunner brokenRunner = new EtlPipelineRunner(brokenRegistry);

            var records = List.of(Map.<String, Object>of("v", "hello"));
            var config = new EtlConfig(List.of(
                    new EtlStepConfig(EtlStepType.TRIM_WHITESPACE, Map.of()),
                    new EtlStepConfig(EtlStepType.STRIP_HTML, Map.of())
            ));

            var result = brokenRunner.run(records, config);
            assertThat(result.stepReports()).hasSize(2);

            var failedReport = result.stepReports().get(0);
            assertThat(failedReport.success()).isFalse();
            assertThat(failedReport.error()).contains("simulated failure");

            var okReport = result.stepReports().get(1);
            assertThat(okReport.success()).isTrue();
        }
    }

    @Nested
    @DisplayName("result metadata")
    class ResultMetadata {

        @Test
        @DisplayName("durationMs is non-negative after execution")
        void run_durationIsNonNegative() {
            var records = List.of(Map.<String, Object>of("v", "x"));
            var config = configOf(new EtlStepConfig(EtlStepType.TRIM_WHITESPACE, Map.of()));

            var result = runner.run(records, config);

            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("rawCount, cleanCount, removedCount are consistent")
        void run_countsAreConsistent() {
            Map<String, Object> dup = Map.of("id", "1");
            var records = new ArrayList<>(List.of(dup, dup,
                    Map.<String, Object>of("id", "2")));
            var config = configOf(new EtlStepConfig(
                    EtlStepType.DEDUPLICATE, Map.of("keys", List.of("id"))));

            var result = runner.run(records, config);

            assertThat(result.rawCount()).isEqualTo(3);
            assertThat(result.cleanCount()).isEqualTo(2);
            assertThat(result.removedCount()).isEqualTo(1);
            assertThat(result.rawCount()).isEqualTo(result.cleanCount() + result.removedCount());
        }

        @Test
        @DisplayName("null params in EtlStepConfig is treated as empty map")
        void run_nullParams_treatedAsEmptyMap() {
            var records = List.of(Map.<String, Object>of("name", "  hello  "));
            var config = configOf(new EtlStepConfig(EtlStepType.TRIM_WHITESPACE, null));
            var result = runner.run(records, config);

            assertThat(result.records().getFirst().get("name")).isEqualTo("hello");
        }
    }


    private EtlConfig configOf(EtlStepConfig... stepConfigs) {
        return new EtlConfig(List.of(stepConfigs));
    }

    private static EtlStepRegistry registryOf(EtlStep... steps) {
        @SuppressWarnings("unchecked")
        Instance<EtlStep> instance = mock(Instance.class);
        when(instance.iterator()).thenReturn(List.of(steps).iterator());
        return new EtlStepRegistry(instance);
    }
}