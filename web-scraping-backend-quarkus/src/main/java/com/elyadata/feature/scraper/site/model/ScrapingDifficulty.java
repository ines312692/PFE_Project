package com.elyadata.feature.scraper.site.model;

public enum ScrapingDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXTREME;

    public static ScrapingDifficulty fromScore(int score) {
        if (score <= 25) return EASY;
        if (score <= 55) return MEDIUM;
        if (score <= 80) return HARD;
        return EXTREME;
    }
}