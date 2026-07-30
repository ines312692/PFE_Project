package com.elyadata.feature.job.application.dto;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.job.domain.model.SiteSignal;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.model.DifficultyFactor;
import com.elyadata.feature.scraper.site.model.OriginIpCandidate;
import com.elyadata.feature.scraper.site.model.ScrapingDifficulty;

import java.util.List;

public record SiteProbeResponse(
        String url,
        List<SiteSignal> signals,
        int httpStatus,
        boolean reachable,
        DetectedPagination detectedPagination,
        List<ProtectionDto> protections,
        DifficultyDto difficulty,
        Boolean robotsStrict,
        List<OriginIpCandidateDto> originCandidates
) {


    public SiteProbeResponse(String url, List<SiteSignal> signals, int httpStatus, boolean reachable) {
        this(url, signals, httpStatus, reachable, null, null, null, null, List.of());
    }

    public SiteProbeResponse(String url, List<SiteSignal> signals, int httpStatus,
                             boolean reachable, DetectedPagination detectedPagination) {
        this(url, signals, httpStatus, reachable, detectedPagination, null, null, null, List.of());
    }

    public SiteProbeResponse(String url,
                             List<SiteSignal> signals,
                             int httpStatus,
                             boolean reachable,
                             DetectedPagination detectedPagination,
                             List<ProtectionDto> protections,
                             DifficultyDto difficulty,
                             Boolean robotsStrict) {
        this(url, signals, httpStatus, reachable, detectedPagination,
                protections, difficulty, robotsStrict, List.of());
    }


    public record ProtectionDto(
            AntiBotProvider provider,
            double confidence,
            String evidence
    ) {
    }

    public record DifficultyDto(
            ScrapingDifficulty level,
            int score,
            String recommendation,
            List<FactorDto> factors
    ) {
        public record FactorDto(
                String code,
                String label,
                int points,
                String description
        ) {
            public static FactorDto from(DifficultyFactor f) {
                return new FactorDto(f.code(), f.label(), f.points(), f.description());
            }
        }
    }

    public record DetectedPagination(
            PaginationType type,
            double confidence,
            String confidenceLevel,
            String reason,
            String suggestedSelector,
            List<Candidate> allCandidates,
            boolean deferredDetection
    ) {
        public DetectedPagination(
                PaginationType type,
                double confidence,
                String confidenceLevel,
                String reason,
                String suggestedSelector,
                List<Candidate> allCandidates
        ) {
            this(type, confidence, confidenceLevel, reason, suggestedSelector, allCandidates, false);
        }

        public record Candidate(
                PaginationType type,
                double confidence,
                String reason
        ) {
        }
    }

    public record OriginIpCandidateDto(
            String ip,
            String discoverySource,
            String validationStatus,
            Integer validationHttpStatus,
            Integer validationLatencyMs,
            double confidence,
            boolean usable
    ) {
        public static OriginIpCandidateDto from(OriginIpCandidate c) {
            return new OriginIpCandidateDto(
                    c.ip(),
                    c.discoverySource(),
                    c.validationStatus().name(),
                    c.validationHttpStatus(),
                    c.validationLatencyMs(),
                    c.confidence(),
                    c.isUsable()
            );
        }
    }
}