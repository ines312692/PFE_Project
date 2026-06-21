package com.elyadata.feature.scraper.antibot.captcha.solvers.llm;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HcaptchaPromptFactory {

    public String dragPuzzlePrompt(int pieceCount, String instruction) {
        return """
    You are solving an hCaptcha "drag the missing piece" puzzle.

    WHAT YOU SEE:
    - A MAIN PATTERN at the top — typically a grid/tiled motif with ONE EMPTY (cut-out) slot.
      The empty slot looks BLANK, FADED, GRAYED-OUT, TRANSPARENT, or has a different
      background than the surrounding filled tiles. It is rarely at exact center.
    - %d CANDIDATE PIECES at the bottom, numbered 0..%d in RED boxes (top-left corner).
    - Instruction: "%s"

    CRITICAL RULE — DO NOT DEFAULT TO (50, 50):
    Returning slot_x_pct=50 AND slot_y_pct=50 is only correct if the empty slot is truly
    in the DEAD CENTER. If you are uncertain, pick the most likely OFF-CENTER position
    rather than defaulting to (50, 50). Empty slots are typically in corners, edges,
    or specific grid cells — not always center.

    REASONING — be deliberate, follow these steps:
    Step 1: Scan the main pattern tile by tile. WHERE is the empty/blank/cut-out tile?
            Describe its location precisely (e.g. "top-right corner", "row 2 column 3").
    Step 2: Convert that location to percentages of the pattern's bounding box:
            slot_x_pct: 0=far-left, 100=far-right
            slot_y_pct: 0=top, 100=bottom
            Round to nearest 10. Examples: top-right ≈ (85, 20), middle-left ≈ (20, 50),
            bottom-center ≈ (50, 85), dead-center ≈ (50, 50).
    Step 3: For each candidate piece (0..%d), check which one would visually complete
            the empty slot — matching color, shape, letter, texture continuity.

    OUTPUT — ONLY this JSON, no markdown, no prose before/after:
    {"piece_index": <int>, "slot_x_pct": <int>, "slot_y_pct": <int>, "confidence": <0.0-1.0>, "reasoning": "<short>"}

    EXAMPLES:
    {"piece_index": 0, "slot_x_pct": 80, "slot_y_pct": 25, "confidence": 0.85, "reasoning": "empty tile top-right; piece 0 matches M letter"}
    {"piece_index": 2, "slot_x_pct": 20, "slot_y_pct": 70, "confidence": 0.9, "reasoning": "blank slot bottom-left; piece 2 continues diagonal"}
    """.formatted(pieceCount, pieceCount - 1, instruction, pieceCount - 1);
    }
}