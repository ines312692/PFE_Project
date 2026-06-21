package com.elyadata.feature.scraper.site.model;

import java.util.List;

public record DifficultyScore(
        ScrapingDifficulty level,
        int score,
        String recommendation,
        List<DifficultyFactor> factors
) {
}