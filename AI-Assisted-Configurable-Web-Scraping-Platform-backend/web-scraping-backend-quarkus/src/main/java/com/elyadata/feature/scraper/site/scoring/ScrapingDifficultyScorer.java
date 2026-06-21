package com.elyadata.feature.scraper.site.scoring;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.job.domain.model.SiteSignal;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import com.elyadata.feature.scraper.site.model.DifficultyFactor;
import com.elyadata.feature.scraper.site.model.DifficultyScore;
import com.elyadata.feature.scraper.site.model.ScrapingDifficulty;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@ApplicationScoped
public class ScrapingDifficultyScorer {

    public DifficultyScore score(ScoringInput input) {
        List<DifficultyFactor> factors = new ArrayList<>();
        int total = 0;

        total += scoreProtections(input.protections(), factors);
        total += scoreJavascript(input.signals(), factors);
        total += scorePagination(input.paginationType(), factors);
        total += scoreRobotsStrict(input.robotsStrict(), factors);
        total += scoreHostileStatus(input.httpStatus(), factors);
        total += scoreMultiProtectionBonus(input.protections(), factors);

        int clamped = Math.clamp(total, 0, 100);
        ScrapingDifficulty level = ScrapingDifficulty.fromScore(clamped);

        DifficultyScore result = new DifficultyScore(
                level, clamped, recommendationFor(level), factors);

        log.debug("[DIFFICULTY] score={} level={} factors={}", clamped, level, factors.size());
        return result;
    }


    private int scoreProtections(List<DetectedProtection> protections, List<DifficultyFactor> factors) {
        if (protections == null || protections.isEmpty()) return 0;

        int sum = 0;
        for (DetectedProtection p : protections) {
            int points = weightFor(p.provider());
            if (points == 0) continue;

            factors.add(new DifficultyFactor(
                    p.provider().name() + "_DETECTED",
                    labelFor(p.provider()),
                    points,
                    p.evidence()
            ));
            sum += points;
        }
        return sum;
    }

    private int scoreJavascript(List<SiteSignal> signals, List<DifficultyFactor> factors) {
        if (signals == null) return 0;
        if (!signals.contains(SiteSignal.LIKELY_JS_RENDERED)) return 0;

        int points = 10;
        factors.add(new DifficultyFactor(
                "JS_RENDERED",
                "JavaScript-rendered (SPA)",
                points,
                "Page content is rendered client-side, requires a stealth browser."
        ));
        return points;
    }

    private int scorePagination(PaginationType type, List<DifficultyFactor> factors) {
        if (type == null) return 0;

        return switch (type) {
            case INFINITE_SCROLL -> {
                int points = 5;
                factors.add(new DifficultyFactor(
                        "PAGINATION_INFINITE_SCROLL",
                        "Infinite-scroll pagination",
                        points,
                        "Requires simulating scroll events to load more content."
                ));
                yield points;
            }
            case AUTO -> {
                int points = 5;
                factors.add(new DifficultyFactor(
                        "PAGINATION_AUTO",
                        "Auto-detect pagination",
                        points,
                        "Pagination type will be resolved at runtime; less predictable."
                ));
                yield points;
            }
            default -> 0;
        };
    }

    private int scoreRobotsStrict(boolean robotsStrict, List<DifficultyFactor> factors) {
        if (!robotsStrict) return 0;

        int points = 10;
        factors.add(new DifficultyFactor(
                "ROBOTS_STRICT",
                "robots.txt disallows crawling",
                points,
                "The site's robots.txt forbids access to this path. Ethical/legal flag."
        ));
        return points;
    }

    private int scoreHostileStatus(int httpStatus, List<DifficultyFactor> factors) {
        if (httpStatus != 403 && httpStatus != 503) return 0;

        int points = 10;
        factors.add(new DifficultyFactor(
                "HOSTILE_STATUS_" + httpStatus,
                "HTTP " + httpStatus + " on first probe",
                points,
                "Site is already hostile to the standard user agent — challenge in place."
        ));
        return points;
    }

    private int scoreMultiProtectionBonus(List<DetectedProtection> protections, List<DifficultyFactor> factors) {
        if (protections == null || protections.size() < 2) return 0;

        int points = 15;
        factors.add(new DifficultyFactor(
                "MULTI_PROTECTION_STACK",
                protections.size() + " anti-bot products combined",
                points,
                "Stacked protections multiply the bypass complexity."
        ));
        return points;
    }


    private int weightFor(AntiBotProvider provider) {
        return switch (provider) {
            case CLOUDFLARE, PERIMETER_X -> 30;
            case DATADOME, AKAMAI -> 35;
            case HCAPTCHA, RECAPTCHA -> 25;
            default -> 0;
        };
    }

    private String labelFor(AntiBotProvider provider) {
        return switch (provider) {
            case CLOUDFLARE -> "Cloudflare protection";
            case DATADOME -> "DataDome bot management";
            case AKAMAI -> "Akamai Bot Manager";
            case PERIMETER_X -> "PerimeterX (HUMAN Security)";
            case HCAPTCHA -> "hCaptcha challenge";
            case RECAPTCHA -> "Google reCAPTCHA challenge";
            default -> provider.name();
        };
    }

    private String recommendationFor(ScrapingDifficulty level) {
        return switch (level) {
            case EASY -> "Direct scraping should work. No special configuration needed.";
            case MEDIUM -> "Enable stealth browser mode. Standard proxy rotation recommended.";
            case HARD -> "Stealth browser is mandatory. Use residential or premium proxies.";
            case EXTREME ->
                    "Expect frequent blocks even with stealth. Consider CAPTCHA solver and dedicated residential IPs.";
        };
    }


    public record ScoringInput(
            List<DetectedProtection> protections,
            List<SiteSignal> signals,
            PaginationType paginationType,
            boolean robotsStrict,
            int httpStatus
    ) {
    }
}