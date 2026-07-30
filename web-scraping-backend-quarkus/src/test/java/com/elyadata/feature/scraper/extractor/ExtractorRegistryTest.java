package com.elyadata.feature.scraper.extractor;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.job.domain.model.ExtractionMode;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ExtractorRegistry")
class ExtractorRegistryTest {

    @SuppressWarnings("unchecked")
    private static ExtractorRegistry registryWith(List<BaseExtractor> extractors) {
        Instance<BaseExtractor> instance = mock(Instance.class);
        when(instance.spliterator()).thenReturn(extractors.spliterator());
        return new ExtractorRegistry(instance);
    }

    private static ExtractorRegistry fullRegistry() {
        return registryWith(List.of(
                new AutoExtractor(),
                new LlmExtractor(),
                new RegexExtractor(),
                new CssExtractor(),
                new XPathExtractor()
        ));
    }

    @ParameterizedTest(name = "resolves extractor for {0}")
    @EnumSource(value = ExtractionMode.class,
            names = {"AUTO", "LLM", "REGEX", "CSS_SELECTOR", "XPATH"})
    @DisplayName("get() returns non-null extractor for every registered mode")
    void get_returnsExtractorForRegisteredModes(ExtractionMode mode) {
        ExtractorRegistry registry = fullRegistry();

        BaseExtractor result = registry.get(mode);

        assertThat(result).isNotNull();
        assertThat(result.mode()).isEqualTo(mode);
    }

    @Test
    @DisplayName("get() returns the correct concrete type for AUTO")
    void get_autoMode_returnsAutoExtractor() {
        ExtractorRegistry registry = fullRegistry();

        assertThat(registry.get(ExtractionMode.AUTO)).isInstanceOf(AutoExtractor.class);
    }

    @Test
    @DisplayName("get() returns the correct concrete type for LLM")
    void get_llmMode_returnsLlmExtractor() {
        ExtractorRegistry registry = fullRegistry();

        assertThat(registry.get(ExtractionMode.LLM)).isInstanceOf(LlmExtractor.class);
    }

    @Test
    @DisplayName("get() returns the correct concrete type for REGEX")
    void get_regexMode_returnsRegexExtractor() {
        ExtractorRegistry registry = fullRegistry();

        assertThat(registry.get(ExtractionMode.REGEX)).isInstanceOf(RegexExtractor.class);
    }

    @Test
    @DisplayName("get() returns the correct concrete type for CSS_SELECTOR")
    void get_cssMode_returnsCssExtractor() {
        ExtractorRegistry registry = fullRegistry();

        assertThat(registry.get(ExtractionMode.CSS_SELECTOR)).isInstanceOf(CssExtractor.class);
    }

    @Test
    @DisplayName("get() returns the correct concrete type for XPATH")
    void get_xpathMode_returnsXPathExtractor() {
        ExtractorRegistry registry = fullRegistry();

        assertThat(registry.get(ExtractionMode.XPATH)).isInstanceOf(XPathExtractor.class);
    }

    @Test
    @DisplayName("get() throws DomainException when mode has no registered extractor")
    void get_unknownMode_throwsDomainException() {
        // Registry built with only one extractor → other modes missing
        ExtractorRegistry registry = registryWith(List.of(new AutoExtractor()));

        assertThatThrownBy(() -> registry.get(ExtractionMode.REGEX))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("REGEX");
    }

    @Test
    @DisplayName("get() error message lists available modes")
    void get_unknownMode_errorMessageListsAvailableModes() {
        ExtractorRegistry registry = registryWith(List.of(new AutoExtractor()));

        assertThatThrownBy(() -> registry.get(ExtractionMode.LLM))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("AUTO");
    }

    @Test
    @DisplayName("each registered extractor has a unique mode (no collision)")
    void registry_noModeCollision() {
        ExtractorRegistry registry = fullRegistry();

        long distinctModes = Stream.of(
                ExtractionMode.AUTO,
                ExtractionMode.LLM,
                ExtractionMode.REGEX,
                ExtractionMode.CSS_SELECTOR,
                ExtractionMode.XPATH
        ).map(m -> registry.get(m).mode()).distinct().count();

        assertThat(distinctModes).isEqualTo(5);
    }
}
