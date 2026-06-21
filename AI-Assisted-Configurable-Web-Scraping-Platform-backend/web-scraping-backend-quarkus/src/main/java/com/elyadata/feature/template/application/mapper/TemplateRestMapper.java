package com.elyadata.feature.template.application.mapper;

import com.elyadata.feature.template.application.dto.TemplateResponse;
import com.elyadata.feature.template.domain.model.Template;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TemplateRestMapper {

    public TemplateResponse toResponse(Template template) {
        if (template == null) return null;
        return TemplateResponse.builder()
                .id(template.id())
                .name(template.name())
                .description(template.description())
                .category(template.category().name())
                .jobType(template.jobType().name())
                .tags(template.tags())
                .popularity(template.popularity())
                .config(template.config())
                .build();
    }

    public List<TemplateResponse> toResponseList(List<Template> templates) {
        if (templates == null) return List.of();
        return templates.stream().map(this::toResponse).toList();
    }
}