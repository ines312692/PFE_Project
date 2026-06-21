package com.elyadata.feature.schema.domain.service;

import com.elyadata.feature.schema.application.dto.SchemaValidationRequest.FieldToValidate;

import java.util.List;
import java.util.stream.Collectors;

public final class SchemaValidatorPrompt {

    private SchemaValidatorPrompt() {
    }

    private static final String SYSTEM_PROMPT = """
            You are a CSS selector specialist for a web scraping platform.
            The user is DESIGNING a job and asks you to suggest a working CSS selector
            for one or more fields whose current selectors do NOT match anything on the page.
            
            You receive:
              - the HTML of the target page (cleaned and sampled to highlight repeating content)
              - a list of fields, each with: name, semantic type, the user's CURRENT selector that fails
            
            For each field, propose a NEW CSS selector that actually matches relevant elements.
            
            STRICT RULES — violations will be rejected automatically:
            1. NEVER propose generic tags: title, body, html, head, div, span, p, a,
               h1, h2, h3, h4, *, main, article, section.
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
            7. If you genuinely cannot find a usable selector for a field, OMIT it
               from the response rather than guessing wildly.
            
            Output strictly this JSON, no prose, no markdown fences:
            {
              "suggestions": [
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
            FIELDS TO RESOLVE:
            %s
            
            SAMPLED HTML:
            %s
            """;

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String userMessage(String sampledHtml, List<FieldToValidate> fields) {
        String fieldsBlock = fields.stream()
                .map(SchemaValidatorPrompt::describeField)
                .collect(Collectors.joining("\n"));
        return USER_MESSAGE_TEMPLATE.formatted(fieldsBlock, sampledHtml);
    }

    private static String describeField(FieldToValidate f) {
        StringBuilder sb = new StringBuilder();
        sb.append("- name='").append(f.name()).append("'");
        sb.append(" type=").append(f.type());
        if (f.attribute() != null && !f.attribute().isBlank()) {
            sb.append(" attribute='").append(f.attribute()).append("'");
        }
        sb.append(" currentSelector='").append(f.selector()).append("'");
        return sb.toString();
    }
}