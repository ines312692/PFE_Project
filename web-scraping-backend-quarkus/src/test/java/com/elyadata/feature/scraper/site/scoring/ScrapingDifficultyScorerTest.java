package com.elyadata.feature.scraper.site.scoring;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.job.domain.model.SiteSignal;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import com.elyadata.feature.scraper.site.model.DifficultyFactor;
import com.elyadata.feature.scraper.site.model.DifficultyScore;
import com.elyadata.feature.scraper.site.model.ScrapingDifficulty;
import com.elyadata.feature.scraper.site.scoring.ScrapingDifficultyScorer.ScoringInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapingDifficultyScorerTest {

    private final ScrapingDifficultyScorer scorer = new ScrapingDifficultyScorer();

    private static DetectedProtection protection(AntiBotProvider provider) {
        return new DetectedProtection(provider, 0.9, "evidence");
    }

    private DifficultyScore score(ScoringInput input) {
        return scorer.score(input);
    }

    private List<String> factorCodes(DifficultyScore s) {
        return s.factors().stream().map(DifficultyFactor::code).toList();
    }

    @Test
    void score_noSignalsYieldsEasyZero() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(), null, false, 200));

        assertThat(result.score()).isZero();
        assertThat(result.level()).isEqualTo(ScrapingDifficulty.EASY);
        assertThat(result.factors()).isEmpty();
        assertThat(result.recommendation()).isNotBlank();
    }

    @Test
    void score_handlesNullCollections() {
        DifficultyScore result = score(new ScoringInput(
                null, null, null, false, 200));

        assertThat(result.score()).isZero();
        assertThat(result.level()).isEqualTo(ScrapingDifficulty.EASY);
    }


    @Test
    void score_cloudflareWeights30() {
        DifficultyScore result = score(new ScoringInput(
                List.of(protection(AntiBotProvider.CLOUDFLARE)), List.of(), null, false, 200));

        assertThat(result.score()).isEqualTo(30);
        assertThat(result.level()).isEqualTo(ScrapingDifficulty.MEDIUM);
        assertThat(factorCodes(result)).contains("CLOUDFLARE_DETECTED");
    }

    @Test
    void score_dataDomeWeights35() {
        DifficultyScore result = score(new ScoringInput(
                List.of(protection(AntiBotProvider.DATADOME)), List.of(), null, false, 200));

        assertThat(result.score()).isEqualTo(35);
    }

    @Test
    void score_hcaptchaWeights25() {
        DifficultyScore result = score(new ScoringInput(
                List.of(protection(AntiBotProvider.HCAPTCHA)), List.of(), null, false, 200));

        assertThat(result.score()).isEqualTo(25);
        assertThat(result.level()).isEqualTo(ScrapingDifficulty.EASY); // 25 -> EASY boundary
    }

    @Test
    void score_jsRenderedAdds10() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(SiteSignal.LIKELY_JS_RENDERED), null, false, 200));

        assertThat(result.score()).isEqualTo(10);
        assertThat(factorCodes(result)).contains("JS_RENDERED");
    }

    @Test
    void score_infiniteScrollAdds5() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(), PaginationType.INFINITE_SCROLL, false, 200));

        assertThat(result.score()).isEqualTo(5);
        assertThat(factorCodes(result)).contains("PAGINATION_INFINITE_SCROLL");
    }

    @Test
    void score_autoPaginationAdds5() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(), PaginationType.AUTO, false, 200));

        assertThat(result.score()).isEqualTo(5);
        assertThat(factorCodes(result)).contains("PAGINATION_AUTO");
    }

    @Test
    void score_robotsStrictAdds10() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(), null, true, 200));

        assertThat(result.score()).isEqualTo(10);
        assertThat(factorCodes(result)).contains("ROBOTS_STRICT");
    }

    @Test
    void score_hostile403Adds10() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(), null, false, 403));

        assertThat(result.score()).isEqualTo(10);
        assertThat(factorCodes(result)).contains("HOSTILE_STATUS_403");
    }

    @Test
    void score_hostile503Adds10() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(), null, false, 503));

        assertThat(result.score()).isEqualTo(10);
        assertThat(factorCodes(result)).contains("HOSTILE_STATUS_503");
    }

    @Test
    void score_normalStatusAddsNothing() {
        DifficultyScore result = score(new ScoringInput(
                List.of(), List.of(), null, false, 404));

        assertThat(result.score()).isZero();
    }

    // ---------------------------------------------------------------------
    // Multi-protection bonus
    // ---------------------------------------------------------------------

    @Test
    void score_twoProtectionsAddStackBonus() {
        DifficultyScore result = score(new ScoringInput(
                List.of(protection(AntiBotProvider.CLOUDFLARE), protection(AntiBotProvider.DATADOME)),
                List.of(), null, false, 200));

        // 30 (CF) + 35 (DataDome) + 15 (stack bonus) = 80
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.level()).isEqualTo(ScrapingDifficulty.HARD);
        assertThat(factorCodes(result)).contains("MULTI_PROTECTION_STACK");
    }

    // ---------------------------------------------------------------------
    // Clamping at 100 / EXTREME
    // ---------------------------------------------------------------------

    @Test
    void score_clampsAt100() {
        DifficultyScore result = score(new ScoringInput(
                List.of(
                        protection(AntiBotProvider.DATADOME),
                        protection(AntiBotProvider.AKAMAI),
                        protection(AntiBotProvider.CLOUDFLARE),
                        protection(AntiBotProvider.PERIMETER_X)),
                List.of(SiteSignal.LIKELY_JS_RENDERED),
                PaginationType.INFINITE_SCROLL, true, 403));
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.level()).isEqualTo(ScrapingDifficulty.EXTREME);
    }


    @Test
    void score_recommendationVariesByLevel() {
        String easy = score(new ScoringInput(List.of(), List.of(), null, false, 200)).recommendation();
        String extreme = score(new ScoringInput(
                List.of(protection(AntiBotProvider.DATADOME), protection(AntiBotProvider.AKAMAI),
                        protection(AntiBotProvider.CLOUDFLARE)),
                List.of(SiteSignal.LIKELY_JS_RENDERED), PaginationType.AUTO, true, 403))
                .recommendation();

        assertThat(easy).isNotBlank();
        assertThat(extreme).isNotBlank();
        assertThat(easy).isNotEqualTo(extreme);
    }
}
