package com.elyadata.feature.schema.application.mapper;

import com.elyadata.feature.schema.application.dto.ExtractionSchemaDto;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface ExtractionSchemaRestMapper {
    @Mapping(target = "jobId", ignore = true)
    ExtractionSchema toDomain(ExtractionSchemaDto dto);
}