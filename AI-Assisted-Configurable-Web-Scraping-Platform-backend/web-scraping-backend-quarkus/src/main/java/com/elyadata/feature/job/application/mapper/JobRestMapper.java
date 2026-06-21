package com.elyadata.feature.job.application.mapper;

import com.elyadata.feature.job.application.dto.*;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.proxy.application.mapper.ProxyRestMapper;
import com.elyadata.feature.schema.application.dto.ExtractionSchemaDto;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "jakarta-cdi", uses = { ProxyRestMapper.class })
public interface JobRestMapper {


    @Mapping(target = "createdByUsername", ignore = true)
    @Mapping(target = "webhookSecret", ignore = true)
    JobResponse toRest(Job job);

    @Mapping(target = "createdByUsername", ignore = true)
    @Named("withSecret")
    JobResponse toRestWithSecret(Job job);

    List<JobResponse> toRest(List<Job> jobs);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "webhookToken", ignore = true)
    @Mapping(target = "webhookSecret", ignore = true)
    @Mapping(target = "webhookEnabled", ignore = true)
    Job toDomain(CreateJobRequest request);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "webhookToken", ignore = true)
    @Mapping(target = "webhookSecret", ignore = true)
    @Mapping(target = "webhookEnabled", ignore = true)
    Job toDomain(UpdateJobRequest request);

    PageAction toDomain(ScraperConfigDto.PageActionDto dto);

    ScraperConfigDto.PageActionDto toPageActionDto(PageAction action);


    @Mapping(target = "jobId", ignore = true)
    ExtractionSchema toDomain(ExtractionSchemaDto dto);

    ExtractionSchemaDto toDto(ExtractionSchema schema);

    @Mapping(source = "session", target = "sessionConfig")
    BrowserConfig toDomain(BrowserConfigDto dto);

    @Mapping(source = "sessionConfig", target = "session")
    BrowserConfigDto toDto(BrowserConfig config);

    ScraperConfig toDomain(ScraperConfigDto dto);
    ScraperConfigDto toDto(ScraperConfig config);

    CrawlerConfig toDomain(CrawlerConfigDto dto);
    CrawlerConfigDto toDto(CrawlerConfig config);

    default SessionConfig toDomain(SessionConfigDto dto) {
        if (dto == null) {
            org.slf4j.LoggerFactory.getLogger(JobRestMapper.class)
                    .info("[MAPPER-REST] SessionConfigDto is NULL → disabled");
            return SessionConfig.disabled();
        }
        org.slf4j.LoggerFactory.getLogger(JobRestMapper.class)
                .info("[MAPPER-REST] DTO received — enabled={} mode='{}' loginUrl={} userSel={}",
                        dto.enabled(), dto.mode(), dto.loginUrl(), dto.usernameSelector());
        return SessionConfig.builder()
                .enabled(dto.enabled())
                .mode(dto.mode() != null && !dto.mode().isBlank()
                        ? SessionConfig.LoginMode.valueOf(dto.mode())
                        : null)
                .loginUrl(dto.loginUrl())
                .usernameSelector(dto.usernameSelector())
                .passwordSelector(dto.passwordSelector())
                .submitSelector(dto.submitSelector())
                .successSelector(dto.successSelector())
                .usernameEnc(dto.username())
                .passwordEnc(dto.password())
                .importedStorageState(dto.importedStorageState())
                .build();
    }
    default SessionConfigDto toDto(SessionConfig sc) {
        if (sc == null) {
            return null;
        }
        return new SessionConfigDto(
                sc.enabled(),
                sc.mode() != null ? sc.mode().name() : null,
                sc.loginUrl(),
                sc.usernameSelector(),
                sc.passwordSelector(),
                sc.submitSelector(),
                sc.successSelector(),
                null,
                null,
                null
        );
    }
}