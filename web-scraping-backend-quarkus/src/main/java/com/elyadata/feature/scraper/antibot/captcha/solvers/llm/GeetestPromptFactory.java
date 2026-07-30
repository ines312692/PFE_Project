package com.elyadata.feature.scraper.antibot.captcha.solvers.llm;

import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class GeetestPromptFactory {

    public String distancePrompt() {
        return """
        You are analyzing a GeeTest slider puzzle CAPTCHA screenshot.
        
        WHAT YOU SEE:
        - A background image (the puzzle area)
        - A small jigsaw puzzle PIECE on the FAR LEFT
        - A dark CUTOUT (hole) somewhere in the image — the piece must slide INTO it
        
        YOUR TASK:
        Determine WHERE the hole is, expressed as a PERCENTAGE of the image's width.
        
        REASONING:
        Step 1: Mentally divide the image into 10 horizontal slices (0% on left, 100% on right).
        Step 2: Locate the LEFT EDGE of the dark hole.
        Step 3: Estimate which slice it falls in (e.g., "slice 7" = 70%).
        Step 4: Round to nearest 5%.
        
        EXPECTED ANSWERS:
        - Hole at 30% (close to left): challenge is easy
        - Hole at 50-60% (middle): typical
        - Hole at 75-85% (far right): hard
        
        RESPONSE FORMAT:
        Brief reasoning (1-2 sentences), THEN this exact line:
        
        FINAL_ANSWER: <number>
        
        Where <number> is a percentage from 25 to 90 (NO % sign, just integer).
        
        EXAMPLE:
        The dark hole is located roughly two-thirds of the way across the image.
        FINAL_ANSWER: 65
        """;
    }

    public String correctionDirectionPrompt() {
        return """
                You are looking at a FAILED GeeTest slider puzzle attempt.
                
                The puzzle piece has been moved but did NOT fit into the hole.
                The piece is currently positioned somewhere on the image, near but NOT in the hole.
                
                YOUR TASK:
                Determine if the piece needs to move FURTHER RIGHT (+) or BACK LEFT (-)
                to reach the hole.
                
                OUTPUT FORMAT:
                Respond with ONLY one of these two characters:
                +   (if piece needs to go further right)
                -   (if piece needs to go back left)
                
                No prose, no explanation. Just + or -.
                """;
    }

    public String bestFitPrompt(int imageCount) {
        return """
        You are looking at a composite image of %d GeeTest puzzle attempts side by side.
        Each sub-image shows the puzzle piece at a DIFFERENT horizontal position.
        Each sub-image has a RED box with its INDEX number (0 to %d) in the top-left corner.
        
        YOUR TASK:
        Find the sub-image where the puzzle piece is PERFECTLY ALIGNED with the dark hole
        (i.e., the piece sits exactly INSIDE the cutout, with NO visible gap).
        
        REASONING (think step by step):
        Step 1: Locate the dark hole in each sub-image.
        Step 2: For each sub-image, observe whether the piece overlaps the hole perfectly,
                or is to the LEFT of the hole, or to the RIGHT.
        Step 3: Choose the index where alignment is PERFECT.
        Step 4: If multiple look similar, pick the one closest to perfect fit.
        
        RESPONSE FORMAT:
        Provide your reasoning in 2-3 sentences, THEN end with this exact line:
        
        FINAL_ANSWER: <index>
        
        Where <index> is an integer between 0 and %d.
        
        EXAMPLE:
        Sub-image 0 shows the piece too far left. Sub-image 1 is closer. Sub-image 2 shows perfect alignment with no visible gap.
        FINAL_ANSWER: 2
        """.formatted(imageCount, imageCount - 1, imageCount - 1);
    }
}