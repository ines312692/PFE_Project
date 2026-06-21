package com.elyadata.feature.scraperesult.infrastructure.adapter;

import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import com.elyadata.feature.scraperesult.infrastructure.mapper.ScrapeResultEntityMapper;
import com.elyadata.feature.scraperesult.infrastructure.repository.ScrapeResultRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@ApplicationScoped
@RequiredArgsConstructor
public class ScrapeResultAdapter implements ScrapeResultPort {

    private final ScrapeResultRepository repository;
    private final ScrapeResultEntityMapper mapper;

    @Override
    public ScrapeResult save(ScrapeResult result) {
        return mapper.toDomain(repository.save(mapper.toEntity(result)));
    }

    @Override
    public Optional<ScrapeResult> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ScrapeResult> findByJobId(UUID jobId) {
        return repository.findByJobId(jobId).map(mapper::toDomain);
    }

    @Override
    public List<ScrapeResult> findAllByJobId(UUID jobId) {
        return repository.findAllByJobId(jobId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}