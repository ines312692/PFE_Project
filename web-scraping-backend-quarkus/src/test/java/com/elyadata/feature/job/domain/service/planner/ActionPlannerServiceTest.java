package com.elyadata.feature.job.domain.service.planner;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmResult;
import com.elyadata.common.llm.observability.LlmCallContext;
import com.elyadata.feature.job.application.dto.ActionPlanRequest;
import com.elyadata.feature.job.application.dto.ActionPlanResponse;
import com.elyadata.feature.job.domain.service.port.PageRendererPort;
import com.elyadata.feature.schema.domain.service.ActionHtmlSampler;
import com.elyadata.feature.schema.domain.service.HtmlSampler;
import com.elyadata.feature.schema.domain.service.LlmSecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionPlannerServiceTest {

    @Mock
    LlmChatClient llm;

    @Spy
    LlmCallContext context = new LlmCallContext();

    @Mock
    PageRendererPort pageRenderer;

    @Mock
    ActionHtmlSampler actionHtmlSampler;

    @Mock
    HtmlSampler htmlSampler;

    @Mock
    LlmSecurityGuard securityGuard;

    @InjectMocks
    ActionPlannerService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(llm.isAvailable()).thenReturn(false);
    }

    @Nested
    @DisplayName("plan() — early exit paths")
    class PlanEarlyExits {

        @Test
        @DisplayName("returns error when llm is not available")
        void llmUnavailable_returnsError() {
            ActionPlanRequest req = new ActionPlanRequest("https://example.com", "click login button", jobId);

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("not configured");
            assertThat(result.actions()).isEmpty();
        }

        @Test
        @DisplayName("returns error when instruction is null")
        void nullInstruction_returnsError() {
            when(llm.isAvailable()).thenReturn(true);
            ActionPlanRequest req = new ActionPlanRequest("https://example.com", null, jobId);

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("describe the steps");
        }

        @Test
        @DisplayName("returns error when instruction is blank")
        void blankInstruction_returnsError() {
            when(llm.isAvailable()).thenReturn(true);
            ActionPlanRequest req = new ActionPlanRequest("https://example.com", "   ", jobId);

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("describe the steps");
        }

        @Test
        @DisplayName("returns error when security guard blocks the instruction")
        void securityGuardBlocks_returnsError() {
            when(llm.isAvailable()).thenReturn(true);
            ActionPlanRequest req = new ActionPlanRequest(
                    "https://example.com",
                    "click the login button",
                    jobId
            );

            when(securityGuard.validateInput(any(), nullable(String.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(LlmSecurityGuard.ValidationResult.denied(
                            "PROMPT_INJECTION", "Injection pattern detected"));

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Injection pattern detected");
        }
    }

    @Nested
    @DisplayName("plan() — fetch and sample paths")
    class FetchAndSample {

        @Test
        @DisplayName("returns error when security guard blocks even if page rendering succeeds")
        void renderedHtml_butSecurityGuardBlocks_returnsError() {
            when(llm.isAvailable()).thenReturn(true);
            ActionPlanRequest req = new ActionPlanRequest(
                    "https://example.com",
                    "perform dangerous action",
                    jobId
            );

            when(securityGuard.validateInput(any(), nullable(String.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(LlmSecurityGuard.ValidationResult.denied(
                            "FORBIDDEN_CONTENT", "Forbidden pattern detected"));

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("Forbidden pattern detected");
        }
    }

    @Nested
    @DisplayName("plan() — LLM call paths")
    class LlmCallPaths {

        @Test
        @DisplayName("returns error when LLM call throws")
        void llmThrows_returnsUnavailableError() {
            when(llm.isAvailable()).thenReturn(true);
            ActionPlanRequest req = new ActionPlanRequest("https://example.com", "click login", jobId);

            when(securityGuard.validateInput(any(), nullable(String.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(true, "click login", null, null));
            when(pageRenderer.renderHtml(anyString(), anyBoolean())).thenReturn(Optional.empty());
            when(htmlSampler.sample(any(), any(Integer.class)))
                    .thenThrow(new RuntimeException("jsoup fetch failed"));
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenThrow(new RuntimeException("groq down"));

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("temporarily unavailable");
        }

        @Test
        @DisplayName("returns error when LLM returns empty response")
        void llmReturnsEmpty_returnsError() {
            when(llm.isAvailable()).thenReturn(true);
            ActionPlanRequest req = new ActionPlanRequest(null, "click login", jobId);

            when(securityGuard.validateInput(any(), nullable(String.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(true, "click login", null, null));
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult("", null, "test-model"));

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Empty response");
        }

        @Test
        @DisplayName("returns planned actions when LLM returns a valid JSON plan")
        void llmReturnsValidPlan_returnsActions() {
            when(llm.isAvailable()).thenReturn(true);
            ActionPlanRequest req = new ActionPlanRequest(null, "click login", jobId);

            when(securityGuard.validateInput(any(), nullable(String.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(true, "click login", null, null));

            String json = """
                    {"actions":[{"type":"CLICK","selector":"#login","optional":false}],"reasoning":"click it"}
                    """;
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult(json, null, "test-model"));

            ActionPlanResponse result = service.plan(req, userId);

            assertThat(result.success()).isTrue();
            assertThat(result.actions()).hasSize(1);
            assertThat(result.actions().getFirst().selector()).isEqualTo("#login");
        }
    }
}
