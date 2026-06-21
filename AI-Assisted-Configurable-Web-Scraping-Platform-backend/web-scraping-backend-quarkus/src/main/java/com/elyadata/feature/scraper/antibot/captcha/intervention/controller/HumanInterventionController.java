package com.elyadata.feature.scraper.antibot.captcha.intervention.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.scraper.antibot.captcha.intervention.dto.HumanInterventionDto;
import com.elyadata.feature.scraper.antibot.captcha.intervention.service.HumanInterventionService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@Path("/v1/captcha-interventions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HumanInterventionController {

    @Inject
    HumanInterventionService service;

    @GET
    @Path("/{jobId}")
    public Response getPending(@PathParam("jobId") UUID jobId) {
        HumanInterventionDto dto = service.findPending(jobId)
                .map(HumanInterventionDto::from)
                .orElseThrow(() -> DomainException.notFound(
                        "No pending intervention for job " + jobId));
        return Response.ok(ApiResponse.success(dto)).build();
    }

    @POST
    @Path("/{jobId}/submit")
    public Response submitToken(@PathParam("jobId") UUID jobId,
                                SubmitTokenRequest body) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            throw DomainException.notValid("Token is required");
        }
        boolean accepted = service.submitToken(jobId, body.token());
        if (!accepted) {
            throw DomainException.notFound(
                    "No pending intervention for job " + jobId + " (may have timed out)");
        }
        return Response.ok(ApiResponse.success("Token accepted, scrape resuming")).build();
    }

    @POST
    @Path("/{jobId}/cancel")
    public Response cancel(@PathParam("jobId") UUID jobId) {
        boolean cancelled = service.cancel(jobId);
        if (!cancelled) {
            throw DomainException.notFound("No pending intervention for job " + jobId);
        }
        return Response.ok(ApiResponse.success("Intervention cancelled")).build();
    }

    @POST
    @Path("/{jobId}/confirm")
    public Response confirmResolved(@PathParam("jobId") UUID jobId) {
        boolean accepted = service.confirmResolved(jobId);
        if (!accepted) {
            throw DomainException.notFound(
                    "No pending interstitial intervention for job " + jobId
                            + " (token-based jobs use /submit)");
        }
        return Response.ok(ApiResponse.success("Confirmation received, verifying…")).build();
    }

    public record SubmitTokenRequest(@NotBlank String token) {
    }
}