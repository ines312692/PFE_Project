package com.elyadata.feature.template.domain.service.port;

import com.elyadata.feature.template.domain.model.Template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplatePort {
    List<Template>     findAll();
    Optional<Template> findById(UUID id);
}