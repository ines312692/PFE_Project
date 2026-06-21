package com.elyadata.feature.dataversion.infrastructure.mapper;

import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.infrastructure.entity.DataVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class DataVersionEntityMapper {

    public DataVersionEntity toEntity(DataVersion domain) {
        if (domain == null) {
            return null;
        }
        return DataVersionEntity.builder()
                .id(domain.id())
                .scrapeResultId(domain.scrapeResultId())
                .versionNumber(domain.versionNumber())
                .parentVersionId(domain.parentVersionId())
                .records(domain.records())
                .appliedSteps(domain.appliedSteps())
                .recordCount(domain.recordCount())
                .label(domain.label())
                .createdBy(domain.createdBy())
                .createdAt(domain.createdAt())
                .build();
    }

    public DataVersion toDomain(DataVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        return DataVersion.builder()
                .id(entity.getId())
                .scrapeResultId(entity.getScrapeResultId())
                .versionNumber(entity.getVersionNumber())
                .parentVersionId(entity.getParentVersionId())
                .records(entity.getRecords())
                .appliedSteps(entity.getAppliedSteps())
                .recordCount(entity.getRecordCount())
                .label(entity.getLabel())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
