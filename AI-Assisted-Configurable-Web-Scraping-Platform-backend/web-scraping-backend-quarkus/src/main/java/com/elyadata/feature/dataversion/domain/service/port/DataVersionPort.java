package com.elyadata.feature.dataversion.domain.service.port;

import com.elyadata.feature.dataversion.domain.model.DataVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataVersionPort {

    DataVersion save(DataVersion version);

    Optional<DataVersion> findById(UUID id);

    List<DataVersion> findByScrapeResultId(UUID scrapeResultId);

    Optional<DataVersion> findLatest(UUID scrapeResultId);

    Optional<DataVersion> findByScrapeResultIdAndVersionNumber(UUID scrapeResultId, int versionNumber);
}
