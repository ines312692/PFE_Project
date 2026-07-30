package com.elyadata.feature.scraper.extractor;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.job.domain.model.ExtractionMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@ApplicationScoped
public class ExtractorRegistry {

    private final Map<ExtractionMode, BaseExtractor> registry;

    @Inject
    public ExtractorRegistry(Instance<BaseExtractor> extractors) {
        this.registry = StreamSupport.stream(extractors.spliterator(), false)
                .collect(Collectors.toMap(BaseExtractor::mode, Function.identity()));
        log.info("[REGISTRY] Loaded extractors: {}", registry.keySet());
    }

    public BaseExtractor get(ExtractionMode mode) {
        BaseExtractor extractor = registry.get(mode);
        if (extractor == null) {
            throw DomainException.invalidExtractionMode(
                    "No extractor registered for mode '" + mode + "'. Available: " + registry.keySet()
            );
        }
        return extractor;
    }
}