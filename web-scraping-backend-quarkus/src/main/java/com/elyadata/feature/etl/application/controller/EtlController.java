package com.elyadata.feature.etl.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.DataVersionService;
import com.elyadata.feature.etl.EtlPipelineRunner;
import com.elyadata.feature.etl.EtlPipelineRunner.EtlRunResult;
import com.elyadata.feature.etl.application.dto.*;
import com.elyadata.feature.etl.domain.model.EtlConfig;
import com.elyadata.feature.etl.domain.model.EtlStepConfig;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Path("/v1/scrape-results/{id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EtlController {

    private final ScrapeResultPort scrapeResultPort;
    private final EtlPipelineRunner etlRunner;
    private final ExportService exportService;
    private final DataVersionService dataVersionService;

    @Context
    SecurityContext securityContext;

    @Inject
    public EtlController(ScrapeResultPort scrapeResultPort,
                         EtlPipelineRunner etlRunner,
                         ExportService exportService,
                         DataVersionService dataVersionService) {
        this.scrapeResultPort = scrapeResultPort;
        this.etlRunner = etlRunner;
        this.exportService = exportService;
        this.dataVersionService = dataVersionService;
    }

    @POST
    @Path("/etl")
    public Response applyEtl(@PathParam("id") UUID id, @Valid EtlRequest request) {
        log.info("[ETL] Apply {} step(s) on scrape-result={} (from v{})",
                request.steps().size(), id, request.fromVersion());

        List<Map<String, Object>> source = resolveSource(id, request.fromVersion());
        EtlRunResult runResult = etlRunner.run(source, toConfig(request));

        return Response.ok(ApiResponse.success(toResponse(id.toString(), runResult, null))).build();
    }

    @POST
    @Path("/etl/apply-and-save")
    public Response applyAndSave(@PathParam("id") UUID id, @Valid EtlRequest request) {
        log.info("[ETL] Apply-and-save {} step(s) on scrape-result={} (from v{})",
                request.steps().size(), id, request.fromVersion());

        List<Map<String, Object>> source = resolveSource(id, request.fromVersion());
        EtlRunResult runResult = etlRunner.run(source, toConfig(request));
        List<Map<String, Object>> appliedSteps = request.steps().stream()
                .map(s -> Map.of(
                        "type", s.type().name(),
                        "params", s.params() != null ? s.params() : Map.of()))
                .toList();

        DataVersion saved = dataVersionService.createVersion(new DataVersionService.NewVersion(
                id,
                runResult.records(),
                appliedSteps,
                request.label(),
                currentUserId()
        ));

        log.info("[ETL] Saved as v{} — {}/{} records, {}ms",
                saved.versionNumber(), runResult.cleanCount(), runResult.rawCount(),
                runResult.durationMs());

        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(toResponse(id.toString(), runResult, saved)))
                .build();
    }

    @POST
    @Path("/export")
    public Response export(@PathParam("id") UUID id, @Valid ExportRequest request) {
        List<Map<String, Object>> source = resolveSource(id, request.fromVersion());
        return Response.ok(ApiResponse.success(
                exportService.export(source, request))).build();
    }

    private List<Map<String, Object>> resolveSource(UUID scrapeResultId, Integer fromVersion) {
        if (fromVersion == null || fromVersion == DataVersion.RAW_VERSION) {
            return findOrThrow(scrapeResultId).records();
        }
        return dataVersionService.getVersion(scrapeResultId, fromVersion).records();
    }

    private ScrapeResult findOrThrow(UUID id) {
        return scrapeResultPort.findById(id)
                .orElseThrow(() -> new NotFoundException("Scrape result not found: " + id));
    }

    private EtlConfig toConfig(EtlRequest req) {
        List<EtlStepConfig> steps = req.steps().stream()
                .map(s -> new EtlStepConfig(s.type(), s.params()))
                .toList();
        return new EtlConfig(steps);
    }

    private UUID currentUserId() {
        Principal p = securityContext != null ? securityContext.getUserPrincipal() : null;
        if (p == null) return null;
        try {
            return UUID.fromString(p.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private EtlResponse toResponse(String id, EtlRunResult r, DataVersion savedVersion) {
        List<StepReportDto> reports = r.stepReports().stream()
                .map(s -> new StepReportDto(s.step(), s.inputCount(),
                        s.outputCount(), s.success(), s.error()))
                .toList();
        return new EtlResponse(
                id, r.records(), reports,
                r.rawCount(), r.cleanCount(), r.removedCount(), r.durationMs(),
                savedVersion != null ? savedVersion.id() : null,
                savedVersion != null ? savedVersion.versionNumber() : null,
                QualityReportDto.from(r.qualityReport())
        );
    }
}