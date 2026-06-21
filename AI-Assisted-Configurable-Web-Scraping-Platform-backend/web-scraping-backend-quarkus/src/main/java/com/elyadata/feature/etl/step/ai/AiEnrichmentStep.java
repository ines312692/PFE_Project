package com.elyadata.feature.etl.step.ai;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmRateLimitException;
import com.elyadata.common.llm.LlmResponseParser;
import com.elyadata.common.llm.LlmUnavailableException;
import com.elyadata.feature.etl.step.EtlStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AiEnrichmentStep implements EtlStep {

    protected static final int MAX_RECORDS = 200;
    protected static final int DEFAULT_BATCH_SIZE = 20;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmChatClient llm;

    protected AiEnrichmentStep(LlmChatClient llm) {
        this.llm = llm;
    }

    protected abstract String systemPrompt(Map<String, Object> params);

    protected abstract String userMessage(List<Map<String, Object>> batch,
                                          Map<String, Object> params);

    protected abstract Map<String, Object> merge(Map<String, Object> entry,
                                                 Map<String, Object> enrichment,
                                                 Map<String, Object> params);

    @Override
    public final List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                                 Map<String, Object> params) {
        if (records == null || records.isEmpty()) {
            return records == null ? List.of() : records;
        }
        if (!llm.isAvailable()) {
            throw new LlmUnavailableException(
                    type() + " requires an LLM provider but none is configured");
        }
        if (records.size() > MAX_RECORDS) {
            throw new IllegalStateException(
                    type() + " refused: " + records.size()
                            + " records exceeds the per-run cap of " + MAX_RECORDS);
        }

        int batchSize = resolveBatchSize(params);
        List<Map<String, Object>> enriched = new ArrayList<>(records.size());

        for (int from = 0; from < records.size(); from += batchSize) {
            int to = Math.min(from + batchSize, records.size());
            enriched.addAll(enrichWithRecovery(records.subList(from, to), params));
        }

        log.info("[ETL-AI] {} — enriched {} record(s) in {} batch(es) of {}",
                type(), enriched.size(),
                (int) Math.ceil((double) records.size() / batchSize), batchSize);
        return List.copyOf(enriched);
    }

    private List<Map<String, Object>> enrichWithRecovery(List<Map<String, Object>> batch,
                                                         Map<String, Object> params) {
        if (batch.isEmpty()) return List.of();

        try {
            return callLlmOnce(batch, params);
        } catch (LlmRateLimitException first) {
            log.warn("[ETL-AI] {} — rate-limited on batch of {}, sleeping {}ms (attempt 1/2)",
                    type(), batch.size(), first.getRetryAfter().toMillis());
            sleepQuietly(first.getRetryAfter());

            try {
                return callLlmOnce(batch, params);
            } catch (LlmRateLimitException second) {
                if (batch.size() > 1) {
                    log.warn("[ETL-AI] {} — still rate-limited after retry, splitting batch {} → {}+{}",
                            type(), batch.size(), batch.size() / 2, batch.size() - batch.size() / 2);
                    int mid = batch.size() / 2;
                    List<Map<String, Object>> left = enrichWithRecovery(batch.subList(0, mid), params);
                    List<Map<String, Object>> right = enrichWithRecovery(batch.subList(mid, batch.size()), params);
                    List<Map<String, Object>> merged = new ArrayList<>(left.size() + right.size());
                    merged.addAll(left);
                    merged.addAll(right);
                    return merged;
                }
                log.error("[ETL-AI] {} — RATE-LIMIT UNRECOVERABLE on single record; preserved without enrichment",
                        type());
                return List.copyOf(batch);
            } catch (Exception e) {
                log.error("[ETL-AI] {} — retry after rate-limit failed ({}); {} record(s) preserved",
                        type(), e.getMessage(), batch.size());
                return List.copyOf(batch);
            }
        } catch (Exception e) {
            log.error("[ETL-AI] {} — batch enrichment failed ({}); {} record(s) preserved",
                    type(), e.getMessage(), batch.size());
            return List.copyOf(batch);
        }
    }

    private List<Map<String, Object>> callLlmOnce(List<Map<String, Object>> batch,
                                                  Map<String, Object> params) {
        String raw = llm.complete(systemPrompt(params), userMessage(batch, params));
        List<Map<String, Object>> enrichments = LlmResponseParser.parseObjectArray(raw);

        if (enrichments.size() != batch.size()) {
            log.warn("[ETL-AI] {} — LLM returned {} enrichment(s) for {} record(s); "
                            + "batch left unchanged",
                    type(), enrichments.size(), batch.size());
            return List.copyOf(batch);
        }

        List<Map<String, Object>> result = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            result.add(merge(batch.get(i), enrichments.get(i), params));
        }
        return result;
    }

    private static void sleepQuietly(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private int resolveBatchSize(Map<String, Object> params) {
        if (params.get("batchSize") instanceof Number n) {
            return Math.clamp(n.intValue(), 1, DEFAULT_BATCH_SIZE * 5);
        }
        return DEFAULT_BATCH_SIZE;
    }

    protected static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    protected static Map<String, Object> copyOf(Map<String, Object> entry) {
        return new LinkedHashMap<>(entry);
    }

    @SuppressWarnings("unchecked")
    protected static List<String> resolveTextFields(List<Map<String, Object>> records,
                                                    Map<String, Object> params) {
        if (params.get("fields") instanceof List<?> list && !list.isEmpty()) {
            return ((List<Object>) list).stream().map(String::valueOf).toList();
        }
        return records.getFirst().entrySet().stream()
                .filter(e -> e.getValue() instanceof String)
                .map(Map.Entry::getKey)
                .toList();
    }

    protected static String joinTextFields(Map<String, Object> entry, List<String> fields) {
        return fields.stream()
                .map(f -> String.valueOf(entry.getOrDefault(f, "")))
                .filter(s -> !s.isBlank())
                .reduce("", (a, b) -> a.isBlank() ? b : a + " — " + b);
    }
}