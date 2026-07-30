package com.elyadata.feature.etl.step.ai;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;


@Singleton
public class SummarizationStep extends AiEnrichmentStep {

    private static final String OUTPUT_FIELD = "_summary";
    private static final int DEFAULT_MAX_WORDS = 40;
    private static final int HARD_MAX_WORDS = 200;

    @Inject
    public SummarizationStep(LlmChatClient llm) {
        super(llm);
    }

    @Override
    public EtlStepType type() {
        return EtlStepType.AI_SUMMARIZE;
    }

    @Override
    protected String systemPrompt(Map<String, Object> params) {
        return """
                You are a summarisation engine.
                Summarise each input text in at most %d words — factual and neutral.
                Return ONLY a JSON array — one object per input text, in the SAME order —
                of the shape {"summary":"<text>"}. No markdown, no commentary.
                """.formatted(maxWords(params));
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
        result.put(OUTPUT_FIELD, enrichment.getOrDefault("summary", ""));
        return result;
    }

    private static int maxWords(Map<String, Object> params) {
        if (params.get("maxWords") instanceof Number n && n.intValue() > 0) {
            return Math.min(n.intValue(), HARD_MAX_WORDS);
        }
        return DEFAULT_MAX_WORDS;
    }
}