package com.elyadata.feature.etl.step.ai;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;


@Singleton
public class NerStep extends AiEnrichmentStep {

    private static final String OUTPUT_FIELD = "_entities";

    @Inject
    public NerStep(LlmChatClient llm) {
        super(llm);
    }

    @Override
    public EtlStepType type() {
        return EtlStepType.AI_NER;
    }

    @Override
    protected String systemPrompt(Map<String, Object> params) {
        return """
                You are a named-entity recognition engine.
                For each input text you receive, extract the named entities it contains.
                Return ONLY a JSON array — one object per input text, in the SAME order —
                where each object has this exact shape:
                {"persons":[],"organizations":[],"locations":[],"dates":[],"amounts":[]}
                Use empty arrays for categories with no entities. No markdown, no comments.
                """;
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
        result.put(OUTPUT_FIELD, enrichment);
        return result;
    }
}