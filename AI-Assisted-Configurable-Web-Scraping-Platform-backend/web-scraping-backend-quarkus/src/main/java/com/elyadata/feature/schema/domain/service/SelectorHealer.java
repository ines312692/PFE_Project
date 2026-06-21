package com.elyadata.feature.schema.domain.service;

import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.domain.model.SchemaField;
import com.elyadata.feature.schema.domain.model.SchemaFieldType;
import com.elyadata.feature.schema.domain.service.SelectorHealerPrompt.RejectionFeedback;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SelectorHealer {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String HEALING_MODEL = "llama-3.3-70b-versatile";
    private static final double MIN_ACCEPTABLE_CONFIDENCE = 0.7;
    private static final int MAX_REASONABLE_MATCHES = 200;
    private static final int MAX_LLM_ATTEMPTS = 2;

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private static final Set<String> FORBIDDEN_GENERIC = Set.of(
            "title", "body", "html", "head", "div", "span", "p", "a",
            "h1", "h2", "h3", "h4", "*", "main", "article", "section"
    );

    @ConfigProperty(name = "llm.groq.api-key", defaultValue = "")
    String groqApiKey;

    private final ExtractionSchemaPort schemaPort;
    private final NotificationService notificationService;
    private final HtmlSampler htmlSampler;
    private final ObjectMapper mapper = new ObjectMapper();

    public HealResult heal(UUID jobId, UUID createdBy, String html) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("[HEAL] Groq API key not configured — skipping job={}", jobId);
            return HealResult.skipped("LLM not configured");
        }
        if (html == null || html.isBlank()) {
            return HealResult.skipped("Empty HTML");
        }

        Optional<ExtractionSchema> schemaOpt = schemaPort.findByJobId(jobId);
        if (schemaOpt.isEmpty() || schemaOpt.get().fields() == null || schemaOpt.get().fields().isEmpty()) {
            return HealResult.skipped("No CSS schema");
        }
        ExtractionSchema schema = schemaOpt.get();

        Document doc = Jsoup.parse(html);
        List<SchemaField> broken = detectBrokenFields(schema.fields(), doc);
        if (broken.isEmpty()) {
            return HealResult.skipped("All selectors still match");
        }

        HtmlSampler.Sample sample = htmlSampler.sample(html);
        if (!sample.hasRepeatingContent()) {
            log.warn("[HEAL] No repeating content detected — page is likely not the right URL " +
                    "(homepage instead of listing?). job={}", jobId);
        }

        log.info("[HEAL] job={} broken={} sampleSize={} chars",
                jobId, broken.stream().map(SchemaField::name).toList(), sample.html().length());

        Map<String, String> validated = runHealingLoop(jobId, sample, broken, doc);

        if (validated.isEmpty()) {
            notifyFailure(jobId, createdBy, broken.size());
            return HealResult.failed("No validated candidates");
        }

        ExtractionSchema repaired = rewriteSchema(schema, validated);
        schemaPort.saveOrUpdate(repaired);
        notifySuccess(jobId, createdBy, validated);
        log.info("[HEAL] job={} repaired {} selector(s)", jobId, validated.size());
        return HealResult.success(validated);
    }


    private Map<String, String> runHealingLoop(UUID jobId,
                                               HtmlSampler.Sample sample,
                                               List<SchemaField> broken,
                                               Document doc) {
        List<RejectionFeedback> rejections = new ArrayList<>();

        for (int attempt = 1; attempt <= MAX_LLM_ATTEMPTS; attempt++) {
            Map<String, SelectorCandidate> candidates = askLlm(sample.html(), broken, rejections, jobId);
            if (candidates.isEmpty()) {
                log.warn("[HEAL] LLM returned no candidates on attempt {}/{} for job={}",
                        attempt, MAX_LLM_ATTEMPTS, jobId);
                continue;
            }

            ValidationOutcome outcome = validateCandidates(candidates, broken, doc);
            if (!outcome.validated().isEmpty()) {
                return outcome.validated();
            }
            log.info("[HEAL] Attempt {}/{} — all candidates rejected, retrying with feedback",
                    attempt, MAX_LLM_ATTEMPTS);
            rejections = outcome.rejections();
        }
        return Map.of();
    }

    private List<SchemaField> detectBrokenFields(List<SchemaField> fields, Document doc) {
        List<SchemaField> broken = new ArrayList<>();
        for (SchemaField f : fields) {
            try {
                if (doc.select(f.selector()).isEmpty()) broken.add(f);
            } catch (Exception e) {
                log.warn("[HEAL] Invalid selector '{}' for '{}': {}", f.selector(), f.name(), e.getMessage());
                broken.add(f);
            }
        }
        return broken;
    }

    private Map<String, SelectorCandidate> askLlm(String sampledHtml,
                                                  List<SchemaField> broken,
                                                  List<RejectionFeedback> rejections,
                                                  UUID jobId) {
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .baseUrl(GROQ_BASE_URL)
                    .apiKey(groqApiKey)
                    .modelName(HEALING_MODEL)
                    .temperature(0.1)
                    .timeout(Duration.ofSeconds(60))
                    .build();

            ChatResponse response = model.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(SelectorHealerPrompt.systemPrompt()),
                            UserMessage.from(SelectorHealerPrompt.userMessage(sampledHtml, broken, rejections))
                    ))
                    .build());

            return parseLlmResponse(response.aiMessage().text());
        } catch (Exception e) {
            log.error("[HEAL] LLM call failed for job={}: {}", jobId, e.getMessage(), e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, SelectorCandidate> parseLlmResponse(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        String clean = raw.strip();
        Matcher fence = JSON_FENCE.matcher(clean);
        clean = fence.find() ? fence.group(1).strip() : clean.replaceAll("```(?:json)?|```", "").strip();

        try {
            Map<String, Object> parsed = mapper.readValue(clean, Map.class);
            if (!(parsed.get("repairs") instanceof List<?> list)) return Map.of();

            Map<String, SelectorCandidate> result = new LinkedHashMap<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> entry) {
                    String name = asString(entry.get("fieldName"));
                    String selector = asString(entry.get("newSelector"));
                    Double conf = asDouble(entry.get("confidence"));
                    String rationale = asString(entry.get("rationale"));

                    if (name != null && selector != null && conf != null) {
                        result.put(name, new SelectorCandidate(selector, conf, rationale));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[HEAL] Failed to parse LLM response: {}", e.getMessage());
            return Map.of();
        }
    }

    private ValidationOutcome validateCandidates(Map<String, SelectorCandidate> candidates,
                                                 List<SchemaField> broken,
                                                 Document doc) {
        Map<String, SchemaField> brokenByName = new HashMap<>();
        broken.forEach(f -> brokenByName.put(f.name(), f));

        Map<String, String> validated = new LinkedHashMap<>();
        List<RejectionFeedback> rejections = new ArrayList<>();

        for (Map.Entry<String, SelectorCandidate> e : candidates.entrySet()) {
            String fieldName = e.getKey();
            SelectorCandidate c = e.getValue();
            SchemaField field = brokenByName.get(fieldName);

            String reason = rejectionReason(fieldName, c, field, doc);
            if (reason != null) {
                log.warn("[HEAL] Rejected '{}' selector='{}': {}", fieldName, c.selector(), reason);
                rejections.add(new RejectionFeedback(fieldName, c.selector(), reason));
                continue;
            }
            log.info("[HEAL] Accepted '{}' → '{}' (conf={}, rationale={})",
                    fieldName, c.selector(), c.confidence(), c.rationale());
            validated.put(fieldName, c.selector());
        }
        return new ValidationOutcome(validated, rejections);
    }

    private String rejectionReason(String fieldName, SelectorCandidate c, SchemaField field, Document doc) {
        if (c.confidence() < MIN_ACCEPTABLE_CONFIDENCE) {
            return "confidence " + c.confidence() + " below threshold " + MIN_ACCEPTABLE_CONFIDENCE;
        }
        String trimmed = c.selector().trim();
        String lower = trimmed.toLowerCase();
        if (FORBIDDEN_GENERIC.contains(lower)) {
            return "selector is a generic tag";
        }
        if (lower.equalsIgnoreCase(fieldName)) {
            return "selector identical to field name (the classic <title>/<body> trap)";
        }
        try {
            var matches = doc.select(trimmed);
            if (matches.isEmpty()) return "matches 0 elements";
            if (matches.size() > MAX_REASONABLE_MATCHES)
                return "matches " + matches.size() + " elements (too broad)";

            if (field != null && requiresNonEmptyText(field.type())) {
                boolean anyContent = matches.stream().anyMatch(this::hasUsableContent);
                if (!anyContent) return "all matched elements have empty text/value";
            }
            return null;
        } catch (Exception e) {
            return "invalid CSS: " + e.getMessage();
        }
    }

    private boolean requiresNonEmptyText(SchemaFieldType type) {
        return type == null
                || type == SchemaFieldType.TEXT
                || type == SchemaFieldType.NUMBER;
    }

    private boolean hasUsableContent(Element el) {
        String t = el.text();
        return !t.isBlank();
    }


    private ExtractionSchema rewriteSchema(ExtractionSchema schema, Map<String, String> validated) {
        List<SchemaField> rewritten = schema.fields().stream()
                .map(f -> validated.containsKey(f.name())
                        ? SchemaField.builder()
                        .name(f.name()).selector(validated.get(f.name()))
                        .type(f.type()).attribute(f.attribute()).required(f.required())
                        .build()
                        : f)
                .toList();

        return ExtractionSchema.builder()
                .id(schema.id()).jobId(schema.jobId()).fields(rewritten)
                .llmPrompt(schema.llmPrompt()).llmModel(schema.llmModel())
                .llmOutputFormat(schema.llmOutputFormat())
                .regexPattern(schema.regexPattern()).regexGroup(schema.regexGroup())
                .build();
    }

    private void notifySuccess(UUID jobId, UUID userId, Map<String, String> repaired) {
        if (userId == null) return;
        try {
            notificationService.notify(userId, jobId, NotificationType.JOB_COMPLETED,
                    "Selectors auto-repaired",
                    repaired.size() + " selector(s) repaired by AI: "
                            + String.join(", ", repaired.keySet())
                            + ". The job will retry with the new selectors.");
        } catch (Exception e) {
            log.warn("[HEAL] notif success failed job={}: {}", jobId, e.getMessage());
        }
    }

    private void notifyFailure(UUID jobId, UUID userId, int brokenCount) {
        if (userId == null) return;
        try {
            notificationService.notify(userId, jobId, NotificationType.JOB_FAILED,
                    "Selector auto-repair failed",
                    "Could not repair " + brokenCount + " broken selector(s). "
                            + "The site structure may have changed — please review the schema manually.");
        } catch (Exception e) {
            log.warn("[HEAL] notif failure failed job={}: {}", jobId, e.getMessage());
        }
    }

    private static String asString(Object o) {
        if (o == null) return null;
        String s = o instanceof String str ? str : o.toString();
        return s.isBlank() ? null : s.strip();
    }

    private static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record SelectorCandidate(String selector, double confidence, String rationale) {
    }

    private record ValidationOutcome(Map<String, String> validated, List<RejectionFeedback> rejections) {
    }

    public record HealResult(boolean repaired, Map<String, String> repairedFields, String reason) {
        public static HealResult success(Map<String, String> fields) {
            return new HealResult(true, fields, null);
        }

        public static HealResult skipped(String reason) {
            return new HealResult(false, Map.of(), reason);
        }

        public static HealResult failed(String reason) {
            return new HealResult(false, Map.of(), reason);
        }
    }
}