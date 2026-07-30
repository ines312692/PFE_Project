package com.elyadata.feature.scraper.antibot.captcha.solvers.llm;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CaptchaPromptFactory {


    public String classifierPrompt() {
        return """
                Look at this CAPTCHA image. Determine its type.
                - "MATH" if it shows an arithmetic expression (e.g. "3 + 5", "12 - 4 = ?")
                - "TEXT" if it shows distorted characters/letters/digits to transcribe
                Respond with ONLY one word: MATH or TEXT. No prose, no JSON, no punctuation.
                """;
    }


    public String textCaptchaPrompt() {
        return """
                You are an expert OCR system for CAPTCHA images.
                The image shows distorted text characters that you must transcribe EXACTLY.
                
                Rules:
                - Output ONLY the characters visible, no spaces, no punctuation.
                - Case-sensitive: distinguish 'O' (letter) from '0' (zero), 'l' from '1', etc.
                - Ignore background noise, lines, dots, gradients.
                - If a character is ambiguous, pick the most likely interpretation.
                - Length is typically 4-8 characters.
                
                Respond with ONLY a JSON object (no markdown, no prose):
                {"answer": "<exact_characters>", "confidence": <0.0-1.0>}
                """;
    }


    public String mathCaptchaPrompt() {
        return """
                You are an expert at solving CAPTCHA math problems.
                The image shows a simple arithmetic expression (addition, subtraction,
                multiplication, or division of small integers).
                
                Steps:
                1. Read the operands and operator carefully.
                2. Compute the result as an integer (no decimals, no spaces).
                3. If division gives a non-integer, round to the nearest integer.
                
                Respond with ONLY a JSON object (no markdown, no prose):
                {"answer": "<integer_result>", "expression": "<what_you_read>", "confidence": <0.0-1.0>}
                """;
    }
}