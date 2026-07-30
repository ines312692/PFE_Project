package com.elyadata.feature.scraperesult.infrastructure.mapper;

import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.infrastructure.entity.ScrapeResultEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface ScrapeResultEntityMapper {

    @Mapping(target = "id", ignore = true)
    ScrapeResultEntity toEntity(ScrapeResult result);

    ScrapeResult toDomain(ScrapeResultEntity entity);
}