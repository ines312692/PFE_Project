package com.elyadata.feature.job.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.dto.SiteProbeRequest;
import com.elyadata.feature.job.application.dto.SiteProbeResponse;
import com.elyadata.feature.job.domain.service.SiteProbeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@Path(SiteProbeController.PROBE_PATH)
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SiteProbeController {

    public static final String PROBE_PATH = "/v1/jobs/probe";

    private final SiteProbeService siteProbeService;
    private final BffTokenService bffToken;

    @POST
    public Response probe(@Valid SiteProbeRequest request) {
        bffToken.resolveCurrentUserId(PROBE_PATH);
        SiteProbeResponse result = siteProbeService.probe(request.url());
        return Response.ok(ApiResponse.success(result)).build();
    }
}