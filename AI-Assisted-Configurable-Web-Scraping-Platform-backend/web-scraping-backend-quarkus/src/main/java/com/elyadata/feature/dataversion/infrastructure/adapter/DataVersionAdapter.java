package com.elyadata.feature.dataversion.infrastructure.adapter;

import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.port.DataVersionPort;
import com.elyadata.feature.dataversion.infrastructure.mapper.DataVersionEntityMapper;
import com.elyadata.feature.dataversion.infrastructure.repository.DataVersionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Transactional
@ApplicationScoped
@RequiredArgsConstructor
public class DataVersionAdapter implements DataVersionPort {

    private final DataVersionRepository repository;
    private final DataVersionEntityMapper mapper;

    @Override
    public DataVersion save(DataVersion version) {
        return mapper.toDomain(repository.save(mapper.toEntity(version)));
    }

    @Override
    public Optional<DataVersion> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<DataVersion> findByScrapeResultId(UUID scrapeResultId) {
        return repository.findByScrapeResultIdOrderByVersionNumberAsc(scrapeResultId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DataVersion> findLatest(UUID scrapeResultId) {
        return repository.findFirstByScrapeResultIdOrderByVersionNumberDesc(scrapeResultId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<DataVersion> findByScrapeResultIdAndVersionNumber(UUID scrapeResultId,
                                                                      int versionNumber) {
        return repository.findByScrapeResultIdAndVersionNumber(scrapeResultId, versionNumber)
                .map(mapper::toDomain);
    }
}
