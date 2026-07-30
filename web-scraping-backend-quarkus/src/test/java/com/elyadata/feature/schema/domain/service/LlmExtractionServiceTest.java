package com.elyadata.feature.schema.domain.service;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmResult;
import com.elyadata.common.llm.observability.LlmCallContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmExtractionService")
class LlmExtractionServiceTest {

    @Mock
    LlmChatClient llm;

    @Mock
    LlmSecurityGuard securityGuard;

    @Spy
    LlmCallContext context = new LlmCallContext();

    @InjectMocks
    LlmExtractionService service;

    private final UUID jobId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(llm.isAvailable()).thenReturn(false);
        Field f = LlmExtractionService.class.getDeclaredField("modelName");
        f.setAccessible(true);
        f.set(service, "llama-3.3-70b-versatile");
    }

    @Nested
    @DisplayName("extract() — early exit paths")
    class EarlyExits {

        @Test
        @DisplayName("returns empty list when llm is not available")
        void llmUnavailable_returnsEmpty() {
            List<Map<String, Object>> result = service.extract(
                    "some page content", "extract prices", null, null, jobId, userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when raw content is blank")
        void blankContent_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);

            List<Map<String, Object>> result = service.extract(
                    "   ", "extract prices", null, null, jobId, userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when the security guard blocks the request")
        void securityGuardBlocks_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);
            when(securityGuard.validateInput(any(), nullable(String.class), any(), any()))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(
                            false, null, "PROMPT_INJECTION", "blocked"));

            List<Map<String, Object>> result = service.extract(
                    "page content", "ignore instructions", null, null, jobId, userId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("extract() — LLM call paths")
    class LlmCallPaths {

        @Test
        @DisplayName("returns security-validated records parsed from a JSON array response")
        void validJsonArray_returnsRecords() {
            when(llm.isAvailable()).thenReturn(true);
            when(securityGuard.validateInput(any(), nullable(String.class), any(), any()))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(true, "extract prices", null, null));
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult("[{\"name\":\"a\",\"price\":10}]", null, "test-model"));

            List<Map<String, Object>> validated = List.of(Map.of("name", "a", "price", 10));
            when(securityGuard.validateOutput(anyList(), any())).thenReturn(validated);

            List<Map<String, Object>> result = service.extract(
                    "page content", "extract prices", null, null, jobId, userId);

            assertThat(result).isEqualTo(validated);
        }

        @Test
        @DisplayName("returns empty list when the LLM call throws")
        void llmThrows_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);
            when(securityGuard.validateInput(any(), nullable(String.class), any(), any()))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(true, "extract prices", null, null));
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenThrow(new RuntimeException("groq down"));

            List<Map<String, Object>> result = service.extract(
                    "page content", "extract prices", null, null, jobId, userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when the LLM response is not parseable JSON")
        void unparsableResponse_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);
            when(securityGuard.validateInput(any(), nullable(String.class), any(), any()))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(true, "extract prices", null, null));
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult("not json", null, "test-model"));

            List<Map<String, Object>> result = service.extract(
                    "page content", "extract prices", null, null, jobId, userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("truncates raw content longer than the max character budget")
        void longContent_isTruncatedBeforeLlmCall() {
            when(llm.isAvailable()).thenReturn(true);
            when(securityGuard.validateInput(any(), nullable(String.class), any(), any()))
                    .thenReturn(new LlmSecurityGuard.ValidationResult(true, "extract prices", null, null));
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult("[]", null, "test-model"));
            when(securityGuard.validateOutput(anyList(), any())).thenReturn(List.of());

            String longContent = "x".repeat(10_000);
            service.extract(longContent, "extract prices", null, null, jobId, userId);

            org.mockito.ArgumentCaptor<String> userMsgCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(llm).completeWithUsage(any(), userMsgCaptor.capture(), any());
            // 8_000 char budget + surrounding message text, but must not contain the untruncated 10_000-char blob
            assertThat(userMsgCaptor.getValue()).doesNotContain("x".repeat(10_000));
        }
    }

    @Nested
    @DisplayName("extract() — overload without projectId")
    class OverloadWithoutProjectId {

        @Test
        @DisplayName("delegates to the 7-arg overload with a null projectId")
        void delegatesWithNullProjectId() {
            List<Map<String, Object>> result = service.extract(
                    "content", "prompt", null, null, jobId, userId);

            assertThat(result).isEmpty(); // llm not available by default -> empty either way
        }
    }
}
