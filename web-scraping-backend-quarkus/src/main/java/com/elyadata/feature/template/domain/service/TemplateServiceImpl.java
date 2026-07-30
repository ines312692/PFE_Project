package com.elyadata.feature.template.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.template.domain.model.Template;
import com.elyadata.feature.template.domain.service.port.TemplatePort;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplatePort templatePort;

    @Override
    public List<Template> getAll() {
        List<Template> templates = templatePort.findAll();
        log.info("[TEMPLATE] Fetched all count={}", templates.size());
        return templates;
    }

    @Override
    public Template getById(UUID id) {
        return templatePort.findById(id)
                .orElseThrow(() -> DomainException.notFound("Template not found: " + id));
    }
}