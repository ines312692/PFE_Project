package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;


@Slf4j
@ApplicationScoped
public class EtlStepRegistry {

    private final Map<EtlStepType, EtlStep> steps = new EnumMap<>(EtlStepType.class);

    @Inject
    public EtlStepRegistry(Instance<EtlStep> discoveredSteps) {
        this((Iterable<EtlStep>) discoveredSteps);
    }

    EtlStepRegistry(EtlStep... steps) {
        this(Arrays.asList(steps));
    }

    private EtlStepRegistry(Iterable<EtlStep> discoveredSteps) {
        for (EtlStep step : discoveredSteps) {
            EtlStep previous = this.steps.put(step.type(), step);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate ETL step registered for type " + step.type() + ": "
                                + previous.getClass().getName() + " and "
                                + step.getClass().getName());
            }
        }
        log.info("[ETL] Registered {} step(s): {}", this.steps.size(), this.steps.keySet());
    }

    public EtlStep get(EtlStepType type) {
        EtlStep step = steps.get(type);
        if (step == null) {
            throw new IllegalArgumentException("Unknown ETL step: " + type);
        }
        return step;
    }

}