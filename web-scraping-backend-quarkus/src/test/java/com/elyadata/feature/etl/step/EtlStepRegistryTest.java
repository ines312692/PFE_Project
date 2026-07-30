package com.elyadata.feature.etl.step;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import com.elyadata.feature.etl.step.ai.*;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EtlStepRegistry")
class EtlStepRegistryTest {

    @Mock
    LlmChatClient llm;
    @Mock
    ExtractionSchemaPort schemaPort;

    private EtlStepRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EtlStepRegistry(
                new CapitalizeStep(),
                new DateNormalizerStep(),
                new DeduplicateStep(),
                new EnumNormalizerStep(),
                new HtmlStripperStep(),
                new HtmlToRecordsStep(),
                new MissingValueHandlerStep(),
                new PriceNormalizerStep(),
                new TrimWhitespaceStep(),

                new PiiAnonymizationStep(),
                new ValidateFieldsStep(schemaPort, new ObjectMapper()),

                new NerStep(llm),
                new ClassificationStep(llm),
                new SummarizationStep(llm),
                new AnomalyDetectionStep(llm)
        );
    }


    @Test
    @DisplayName("get returns a non-null step for every EtlStepType value")
    void get_returnsStepForEveryType() {
        for (EtlStepType type : EtlStepType.values()) {
            assertThat(registry.get(type))
                    .as("step for %s should not be null", type)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("get returns the correct implementation for each type")
    void get_returnsCorrectImplementation() {
        for (EtlStepType type : EtlStepType.values()) {
            EtlStep step = registry.get(type);
            assertThat(step.type())
                    .as("step.type() should match the requested type for %s", type)
                    .isEqualTo(type);
        }
    }


    @Test
    @DisplayName("registry has no duplicate type registrations")
    void registry_noTypeCollisions() {
        Set<EtlStepType> seen = EnumSet.noneOf(EtlStepType.class);
        for (EtlStepType type : EtlStepType.values()) {
            assertThatCode(() -> {
                EtlStep step = registry.get(type);
                boolean firstTime = seen.add(step.type());
                assertThat(firstTime)
                        .as("type %s registered more than once", type)
                        .isTrue();
            }).doesNotThrowAnyException();
        }
    }


    @Test
    @DisplayName("duplicate registration throws IllegalStateException")
    void constructor_duplicateStep_throws() {
        assertThatThrownBy(() ->
                new EtlStepRegistry(new CapitalizeStep(), new CapitalizeStep()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate ETL step");
    }
}