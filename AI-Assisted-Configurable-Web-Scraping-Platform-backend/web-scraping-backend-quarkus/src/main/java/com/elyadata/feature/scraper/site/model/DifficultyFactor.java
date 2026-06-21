package com.elyadata.feature.scraper.site.model;

public record DifficultyFactor(
        String code,
        String label,
        int points,
        String description
) {
}