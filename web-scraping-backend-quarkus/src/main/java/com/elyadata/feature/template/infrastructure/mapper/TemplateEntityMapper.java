package com.elyadata.feature.template.infrastructure.mapper;

import com.elyadata.feature.template.domain.model.Template;
import com.elyadata.feature.template.infrastructure.entity.TemplateEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "jakarta")
public interface TemplateEntityMapper {
    Template       toDomain(TemplateEntity entity);
    List<Template> toDomainList(List<TemplateEntity> entities);
}