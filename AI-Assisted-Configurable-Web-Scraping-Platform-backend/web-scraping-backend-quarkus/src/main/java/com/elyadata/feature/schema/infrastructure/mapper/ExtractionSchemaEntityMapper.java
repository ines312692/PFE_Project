package com.elyadata.feature.schema.infrastructure.mapper;

import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.infrastructure.entity.ExtractionSchemaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface ExtractionSchemaEntityMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExtractionSchemaEntity toEntity(ExtractionSchema schema);

    ExtractionSchema toDomain(ExtractionSchemaEntity entity);
}