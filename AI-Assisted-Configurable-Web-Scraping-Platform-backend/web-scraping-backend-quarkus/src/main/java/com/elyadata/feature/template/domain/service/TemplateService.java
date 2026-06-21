package com.elyadata.feature.template.domain.service;

import com.elyadata.feature.template.domain.model.Template;

import java.util.List;
import java.util.UUID;

public interface TemplateService {
    List<Template> getAll();
    Template       getById(UUID id);
}