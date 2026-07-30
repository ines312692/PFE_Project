package com.elyadata.feature.dataversion.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.dataversion.application.dto.DataVersionResponse;
import com.elyadata.feature.dataversion.application.dto.DataVersionSummaryResponse;
import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.DataVersionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;


@Slf4j
@Path("/v1/scrape-results/{scrapeResultId}/versions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DataVersionController {

    private final DataVersionService dataVersionService;

    @Inject
    public DataVersionController(DataVersionService dataVersionService) {
        this.dataVersionService = dataVersionService;
    }

    @GET
    public Response list(@PathParam("scrapeResultId") UUID scrapeResultId) {
        List<DataVersionSummaryResponse> history =
                dataVersionService.listVersions(scrapeResultId).stream()
                        .map(DataVersionController::toSummary)
                        .toList();
        return Response.ok(ApiResponse.success(history)).build();
    }

    @GET
    @Path("/{versionNumber}")
    public Response get(@PathParam("scrapeResultId") UUID scrapeResultId,
                        @PathParam("versionNumber") int versionNumber) {
        DataVersion version = dataVersionService.getVersion(scrapeResultId, versionNumber);
        return Response.ok(ApiResponse.success(toResponse(version))).build();
    }

    @POST
    @Path("/{versionNumber}/rollback")
    public Response rollback(@PathParam("scrapeResultId") UUID scrapeResultId,
                             @PathParam("versionNumber") int versionNumber) {
        log.info("[DATA-VERSION] Rollback requested — scrape-result={} target=v{}",
                scrapeResultId, versionNumber);
        DataVersion created = dataVersionService.rollback(scrapeResultId, versionNumber, null);
        return Response.ok(ApiResponse.success(toResponse(created))).build();
    }


    private static DataVersionResponse toResponse(DataVersion v) {
        return new DataVersionResponse(
                v.id(), v.scrapeResultId(), v.versionNumber(), v.parentVersionId(),
                v.records(), v.appliedSteps(), v.recordCount(),
                v.label(), v.createdBy(), v.createdAt());
    }

    private static DataVersionSummaryResponse toSummary(DataVersion v) {
        return new DataVersionSummaryResponse(
                v.id(), v.scrapeResultId(), v.versionNumber(), v.parentVersionId(),
                v.appliedSteps(), v.recordCount(),
                v.label(), v.createdBy(), v.createdAt());
    }
}
