package com.elyadata.feature.monitoring.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.monitoring.application.dto.PerformanceDto;
import com.elyadata.feature.monitoring.domain.service.PerformanceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/v1/monitoring/performance")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PerformanceController {

    private static final String PERF_PATH = "/v1/monitoring/performance";

    private final PerformanceService service;
    private final BffTokenService bffToken;

    @GET
    @Path("/summary")
    public ApiResponse<PerformanceDto.Summary> summary(@QueryParam("range") String range) {
        return ApiResponse.success(service.summary(range, resolveUser("/summary"), isAdmin()));
    }

    @GET
    @Path("/timeseries")
    public ApiResponse<PerformanceDto.TimeSeries> timeseries(@QueryParam("range") String range) {
        return ApiResponse.success(service.timeseries(range, resolveUser("/timeseries"), isAdmin()));
    }

    @GET
    @Path("/by-engine")
    public ApiResponse<List<PerformanceDto.EngineBreakdown>> byEngine(@QueryParam("range") String range) {
        return ApiResponse.success(service.byEngine(range, resolveUser("/by-engine"), isAdmin()));
    }

    @GET
    @Path("/runs")
    public ApiResponse<List<PerformanceDto.RunPoint>> runs(@QueryParam("range") String range) {
        return ApiResponse.success(service.recentRuns(range, resolveUser("/runs"), isAdmin()));
    }

    private UUID resolveUser(String suffix) {
        return bffToken.resolveCurrentUserId(PERF_PATH + suffix);
    }

    private boolean isAdmin() {
        return bffToken.hasRole("ADMIN");
    }
}