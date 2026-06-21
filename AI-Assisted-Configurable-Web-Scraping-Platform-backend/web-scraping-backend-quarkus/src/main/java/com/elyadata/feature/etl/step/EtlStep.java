package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;

import java.util.List;
import java.util.Map;

public interface EtlStep {

    EtlStepType type();

    List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                    Map<String, Object> params);
}
