package com.elyadata.feature.template.infrastructure.adapter;

import com.elyadata.feature.template.domain.model.Template;
import com.elyadata.feature.template.domain.service.port.TemplatePort;
import com.elyadata.feature.template.infrastructure.mapper.TemplateEntityMapper;
import com.elyadata.feature.template.infrastructure.repository.TemplateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class TemplateAdapter implements TemplatePort {

    private final TemplateRepository   templateRepository;
    private final TemplateEntityMapper templateEntityMapper;

    @Override
    public List<Template> findAll() {
        return templateEntityMapper.toDomainList(templateRepository.findAll());
    }

    @Override
    public Optional<Template> findById(UUID id) {
        return templateRepository.findById(id)
                .map(templateEntityMapper::toDomain);
    }
}