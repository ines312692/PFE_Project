package com.elyadata.feature.dataversion.domain.service;

import com.elyadata.feature.dataversion.domain.model.DataVersion;

import java.util.List;
import java.util.Map;
import java.util.UUID;


public interface DataVersionService {


    DataVersion createVersion(NewVersion command);

    List<DataVersion> listVersions(UUID scrapeResultId);

    DataVersion getVersion(UUID scrapeResultId, int versionNumber);


    DataVersion rollback(UUID scrapeResultId, int targetVersionNumber, UUID userId);

    record NewVersion(
            UUID scrapeResultId,
            List<Map<String, Object>> records,
            List<Map<String, Object>> appliedSteps,
            String label,
            UUID createdBy
    ) {
    }
}
