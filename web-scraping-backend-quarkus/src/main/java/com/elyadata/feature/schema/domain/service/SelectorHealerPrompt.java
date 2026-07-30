package com.elyadata.feature.schema.domain.service;

import com.elyadata.feature.schema.domain.model.SchemaField;

import java.util.List;
import java.util.stream.Collectors;

public final class SelectorHealerPrompt {

    private SelectorHealerPrompt() {
    }

    private static final String SYSTEM_PROMPT = """
            You are a CSS selector repair specialist for a web scraping platform.
            The user provides:
              - the HTML of a page (cleaned and sampled to highlight repeating content)
              - one or more fields whose old CSS selectors no longer match
              - for each field: name, semantic type, and the OLD broken selector
            
            Your job is to propose a NEW CSS selector for each broken field.
            
            STRICT RULES — violations will be rejected automatically:
            1. NEVER propose generic tags like: title, body, html, head, div, span,
               p, a, h1, h2, h3, * — these are always rejected.
            2. NEVER propose a selector identical to the field name (e.g. selector
               'title' for field 'title' is the #1 mistake — DO NOT do this).
            3. The <title> tag in <head> is page metadata, NOT a product/article title.
               For a 'title' field, find the heading INSIDE the repeating content block.
            4. Prefer selectors anchored on stable class names or data-* attributes
               visible in the REPEATING PATTERN blocks below.
            5. For TEXT/NUMBER fields, point to an element whose text content is the value.
               For URL fields, point to <a> (extracted via href).
               For IMAGE fields, point to <img> (extracted via src/data-src).
               For ATTRIBUTE fields, point to the element carrying the attribute.
            6. Confidence must reflect real certainty: 0.9+ only if the selector
               appears in multiple repeating instances. 0.5 if guessing.
            
            Output strictly this JSON, no prose, no markdown fences:
            {
              "repairs": [
                {
                  "fieldName": "<field name>",
                  "newSelector": "<css selector>",
                  "confidence": <0.0-1.0>,
                  "rationale": "<one short sentence>"
                }
              ]
            }
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            BROKEN FIELDS:
            %s
            %s
            SAMPLED HTML:
            %s
            """;

    private static final String REJECTIONS_HEADER =
            "\nPREVIOUS ATTEMPT REJECTED — do NOT repeat these mistakes:\n";

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String userMessage(String sampledHtml,
                                     List<SchemaField> brokenFields,
                                     List<RejectionFeedback> previousRejections) {
        String fields = brokenFields.stream()
                .map(SelectorHealerPrompt::describeField)
                .collect(Collectors.joining("\n"));

        String rejections = formatRejections(previousRejections);

        return USER_MESSAGE_TEMPLATE.formatted(fields, rejections, sampledHtml);
    }

    private static String formatRejections(List<RejectionFeedback> rejections) {
        if (rejections.isEmpty()) return "";
        return REJECTIONS_HEADER + rejections.stream()
                .map(r -> "- field='" + r.fieldName() + "' selector='" + r.selector()
                        + "' reason=" + r.reason())
                .collect(Collectors.joining("\n"))
                + "\n";
    }

    private static String describeField(SchemaField f) {
        StringBuilder sb = new StringBuilder();
        sb.append("- name='").append(f.name()).append("'");
        sb.append(" type=").append(f.type());
        if (f.attribute() != null && !f.attribute().isBlank()) {
            sb.append(" attribute='").append(f.attribute()).append("'");
        }
        sb.append(" oldSelector='").append(f.selector()).append("'");
        return sb.toString();
    }

    public record RejectionFeedback(String fieldName, String selector, String reason) {
    }
}