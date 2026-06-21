package com.elyadata.feature.scraper.site.model;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapingDifficultyTest {

    @ParameterizedTest
    @CsvSource({
            "0, EASY",
            "25, EASY",
            "26, MEDIUM",
            "55, MEDIUM",
            "56, HARD",
            "80, HARD",
            "81, EXTREME",
            "100, EXTREME"
    })
    void fromScore_mapsToCorrectLevel(int score, ScrapingDifficulty expected) {
        assertThat(ScrapingDifficulty.fromScore(score)).isEqualTo(expected);
    }
}
