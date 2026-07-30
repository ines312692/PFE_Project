package com.elyadata.feature.etl.step.ai;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;


@Singleton
public class AnomalyDetectionStep extends AiEnrichmentStep {

    private static final String ANOMALIES_FIELD = "_anomalies";
    private static final String FLAG_FIELD = "_has_anomaly";

    @Inject
    public AnomalyDetectionStep(LlmChatClient llm) {
        super(llm);
    }

    @Override
    public EtlStepType type() {
        return EtlStepType.AI_DETECT_ANOMALIES;
    }

    @Override
    protected String systemPrompt(Map<String, Object> params) {
        return """
                You are a data quality inspector.
                For each input record, detect aberrant values such as:
                negative or zero prices, dates in the future, numbers far outside a
                plausible range, mandatory-looking fields left empty, and fields that
                contradict each other.
                Return ONLY a JSON array — one object per input record, in the SAME order —
                of the shape {"anomalies":[{"field":"<name>","issue":"<short reason>"}]}.
                Use an empty array when the record looks clean. No markdown, no commentary.
                """;
    }

    @Override
    protected String userMessage(List<Map<String, Object>> batch, Map<String, Object> params) {
        return "Records (" + batch.size() + "):\n" + toJson(batch);
    }

    @Override
    protected Map<String, Object> merge(Map<String, Object> entry,
                                        Map<String, Object> enrichment,
                                        Map<String, Object> params) {
        Map<String, Object> result = copyOf(entry);
        Object anomalies = enrichment.getOrDefault("anomalies", List.of());
        boolean hasAnomaly = anomalies instanceof List<?> list && !list.isEmpty();
        result.put(ANOMALIES_FIELD, anomalies);
        result.put(FLAG_FIELD, hasAnomaly);
        return result;
    }
}