package com.elyadata.feature.scraper.browser.llm;

import java.util.List;
import java.util.stream.Collectors;

public final class ActionResolverPrompt {

    private ActionResolverPrompt() {
    }

    private static final String SYSTEM_PROMPT = """
        You are a web-automation targeting assistant. You receive:
          - an INSTRUCTION describing the element a user wants to click
          - optionally a CONTAINER CSS selector naming the area to look inside
          - an HTML FRAGMENT taken from the live page

        Return exactly ONE CSS selector that locates the target on the FULL page.

        RULES — violations are rejected automatically:
        1. Never return a bare generic tag (div, span, a, button, li, ul, body, html, *).
        2. Prefer stable hooks visible in the fragment: class names, data-* attributes,
           aria-* attributes, role.
        3. If the instruction says "first X" or "an X in the list", return a selector that
           matches ALL such items the automation clicks the first match.
        4. Match the element whose VISIBLE TEXT or aria-label literally corresponds to the
           instruction. "Afficher le prix" must map to an element whose own text is
           "Afficher le prix" — NOT a cart link, header icon, menu entry, or anything you
           merely associate with the concept. If nothing matches, return confidence < 0.4.
        5. If a CONTAINER is given, scope your selector under it.
        6. Use standard CSS3 only. No Playwright pseudo-classes like :has-text() or :scope.
        7. confidence: 0.85+ only if the element's visible text matches the instruction
           with a stable hook; ~0.5 if guessing; < 0.4 if no text match.
        8. NEVER justify a selector by what it "contains" or "is related to". Justify ONLY
           by the element's visible text / aria-label / role. A rationale like "contains
           price information" is forbidden — it means you guessed, so lower the confidence.

        Output strict JSON only — no prose, no markdown fences:
        { "selector": "<css>", "confidence": <0.0-1.0>, "rationale": "<one short sentence>" }
        """;

    private static final String USER_TEMPLATE = """
            INSTRUCTION:
            %s
            
            CONTAINER (scope your selector under this if present):
            %s
            %s
            HTML FRAGMENT:
            %s
            """;

    private static final String REJECTIONS_HEADER =
            "\nPREVIOUS ATTEMPT REJECTED — do NOT repeat these:\n";

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String userMessage(String fragment, String instruction,
                                     String containerHint, List<Rejection> rejections) {
        String container = (containerHint == null || containerHint.isBlank())
                ? "(none)" : containerHint;
        return USER_TEMPLATE.formatted(
                instruction, container, formatRejections(rejections), fragment);
    }

    private static String formatRejections(List<Rejection> rejections) {
        if (rejections == null || rejections.isEmpty()) {
            return "";
        }
        return REJECTIONS_HEADER + rejections.stream()
                .map(r -> "- selector='" + r.selector() + "' reason=" + r.reason())
                .collect(Collectors.joining("\n")) + "\n";
    }

    public record Rejection(String selector, String reason) {
    }
}