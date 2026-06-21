package com.elyadata.feature.scraperesult.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/v1/scrape-results")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScrapeResultController {

    private final ScrapeResultPort scrapeResultPort;
    private final ScrapeResultRestMapper mapper;

    @Inject
    public ScrapeResultController(ScrapeResultPort scrapeResultPort,
                                   ScrapeResultRestMapper mapper) {
        this.scrapeResultPort = scrapeResultPort;
        this.mapper = mapper;
    }

    /** GET /v1/scrape-results/job/{jobId}/latest */
    @GET
    @Path("/job/{jobId}/latest")
    public Response getLatestByJobId(@PathParam("jobId") UUID jobId) {
        ScrapeResult result = scrapeResultPort.findByJobId(jobId)
                .orElseThrow(() -> new NotFoundException(
                        "No scrape result found for job: " + jobId));
        return Response.ok(ApiResponse.success(mapper.toResponse(result))).build();
    }

    /** GET /v1/scrape-results/job/{jobId} — full history */
    @GET
    @Path("/job/{jobId}")
    public Response getAllByJobId(@PathParam("jobId") UUID jobId) {
        List<ScrapeResult> results = scrapeResultPort.findAllByJobId(jobId);
        return Response.ok(ApiResponse.success(
                results.stream().map(mapper::toResponse).toList())).build();
    }

    /** GET /v1/scrape-results/{id} */
    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        ScrapeResult result = scrapeResultPort.findById(id)
                .orElseThrow(() -> new NotFoundException("Scrape result not found: " + id));
        return Response.ok(ApiResponse.success(mapper.toResponse(result))).build();
    }
}
