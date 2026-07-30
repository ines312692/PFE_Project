package com.elyadata.common.llm.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmCallContext")
class LlmCallContextTest {

    private final LlmCallContext context = new LlmCallContext();

    @AfterEach
    void cleanup() {
        context.assertClean("test-cleanup");
    }

    @Test
    @DisplayName("current() is empty when nothing has been opened")
    void current_emptyByDefault() {
        assertThat(context.current()).isEmpty();
    }

    @Nested
    @DisplayName("open()/close()")
    class OpenClose {

        @Test
        @DisplayName("open() populates the scope and close() clears it")
        void open_thenClose_restoresEmpty() {
            UUID jobId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            try (var ignored = context.open(LlmFeature.EXTRACTION, jobId, projectId, userId)) {
                assertThat(context.current()).isPresent();
                LlmCallContext.Scope scope = context.current().get();
                assertThat(scope.feature()).isEqualTo(LlmFeature.EXTRACTION);
                assertThat(scope.jobId()).isEqualTo(jobId);
                assertThat(scope.projectId()).isEqualTo(projectId);
                assertThat(scope.userId()).isEqualTo(userId);
            }

            assertThat(context.current()).isEmpty();
        }

        @Test
        @DisplayName("null feature defaults to UNKNOWN")
        void nullFeature_defaultsToUnknown() {
            try (var ignored = context.open(null, null, null, null)) {
                assertThat(context.current()).isPresent();
                assertThat(context.current().get().feature()).isEqualTo(LlmFeature.UNKNOWN);
            }
        }

        @Test
        @DisplayName("nested open() restores the previous scope on close")
        void nestedOpen_restoresOuterScopeOnInnerClose() {
            UUID outerJob = UUID.randomUUID();
            UUID innerJob = UUID.randomUUID();

            try (var outer = context.open(LlmFeature.EXTRACTION, outerJob, null, null)) {
                try (var inner = context.open(LlmFeature.SCHEMA_ASSIST, innerJob, null, null)) {
                    assertThat(context.current().get().jobId()).isEqualTo(innerJob);
                }
                assertThat(context.current().get().jobId()).isEqualTo(outerJob);
                assertThat(context.current().get().feature()).isEqualTo(LlmFeature.EXTRACTION);
            }
            assertThat(context.current()).isEmpty();
        }
    }

    @Nested
    @DisplayName("call()/run()")
    class CallAndRun {

        @Test
        @DisplayName("call() opens the scope for the duration of the supplier and clears it after")
        void call_returnsSupplierResultAndClearsScope() {
            UUID jobId = UUID.randomUUID();

            String result = context.call(LlmFeature.ACTION_PLANNER, jobId, null, null,
                    () -> {
                        assertThat(context.current()).isPresent();
                        assertThat(context.current().get().feature()).isEqualTo(LlmFeature.ACTION_PLANNER);
                        return "done";
                    });

            assertThat(result).isEqualTo("done");
            assertThat(context.current()).isEmpty();
        }

        @Test
        @DisplayName("call() clears the scope even if the supplier throws")
        void call_clearsScopeOnException() {
            try {
                context.call(LlmFeature.ACTION_PLANNER, null, null, null, () -> {
                    throw new RuntimeException("boom");
                });
            } catch (RuntimeException ignored) {
                // expected
            }
            assertThat(context.current()).isEmpty();
        }

        @Test
        @DisplayName("run() opens the scope for the duration of the runnable and clears it after")
        void run_executesRunnableAndClearsScope() {
            UUID jobId = UUID.randomUUID();
            boolean[] executed = {false};

            context.run(LlmFeature.PAGINATION, jobId, null, null, () -> {
                executed[0] = true;
                assertThat(context.current().get().feature()).isEqualTo(LlmFeature.PAGINATION);
            });

            assertThat(executed[0]).isTrue();
            assertThat(context.current()).isEmpty();
        }
    }

    @Nested
    @DisplayName("assertClean()")
    class AssertClean {

        @Test
        @DisplayName("does nothing when no scope is leaked")
        void noLeak_doesNothing() {
            context.assertClean("somewhere");
            assertThat(context.current()).isEmpty();
        }

        @Test
        @DisplayName("force-clears a leaked scope")
        void leakedScope_isForceCleared() {
            LlmCallContext.Handle handle = context.open(LlmFeature.EXTRACTION, null, null, null);
            // simulate a leak: never call handle.close()

            context.assertClean("somewhere");

            assertThat(context.current()).isEmpty();
            handle.close(); // no-op cleanup of the (already cleared) handle to avoid affecting other tests
        }
    }
}