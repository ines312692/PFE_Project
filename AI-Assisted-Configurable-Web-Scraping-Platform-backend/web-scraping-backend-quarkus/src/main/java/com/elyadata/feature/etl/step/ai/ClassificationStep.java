package com.elyadata.feature.etl.step.ai;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;


@Singleton
public class ClassificationStep extends AiEnrichmentStep {

    private static final String CATEGORY_FIELD = "_category";
    private static final String CONFIDENCE_FIELD = "_category_confidence";
    private static final List<String> DEFAULT_LABELS =
            List.of("product", "article", "listing", "review", "profile", "other");

    @Inject
    public ClassificationStep(LlmChatClient llm) {
        super(llm);
    }

    @Override
    public EtlStepType type() {
        return EtlStepType.AI_CLASSIFY;
    }

    @Override
    protected String systemPrompt(Map<String, Object> params) {
        return """
                You are a text classification engine.
                Classify each input text into EXACTLY ONE of these categories:
                %s
                Return ONLY a JSON array — one object per input text, in the SAME order —
                of the shape {"category":"<one category>","confidence":<number 0..1>}.
                If none fits, use the category "other". No markdown, no commentary.
                """.formatted(String.join(", ", labels(params)));
    }

    @Override
    protected String userMessage(List<Map<String, Object>> batch, Map<String, Object> params) {
        List<String> fields = resolveTextFields(batch, params);
        List<String> texts = batch.stream()
                .map(entry -> joinTextFields(entry, fields))
                .toList();
        return "Texts (" + texts.size() + "):\n" + toJson(texts);
    }

    @Override
    protected Map<String, Object> merge(Map<String, Object> entry,
                                        Map<String, Object> enrichment,
                                        Map<String, Object> params) {
        Map<String, Object> result = copyOf(entry);
        result.put(CATEGORY_FIELD, enrichment.getOrDefault("category", "other"));
        result.put(CONFIDENCE_FIELD, enrichment.get("confidence"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> labels(Map<String, Object> params) {
        if (params.get("labels") instanceof List<?> list && !list.isEmpty()) {
            return ((List<Object>) list).stream().map(String::valueOf).toList();
        }
        return DEFAULT_LABELS;
    }
}