package com.elyadata.feature.llmusage.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.llmusage.application.dto.ProjectLlmUsageResponse;
import com.elyadata.feature.llmusage.domain.service.ProjectLlmUsageService;
import com.elyadata.feature.project.domain.service.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@Path("/v1/projects/{projectId}/llm-usage")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ProjectLlmUsageController {

    private final ProjectLlmUsageService service;
    private final BffTokenService bffToken;
    private final ProjectService projectService;

    @GET
    public Response get(
            @PathParam("projectId") UUID projectId,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {

        UUID userId = bffToken.resolveCurrentUserId(
                "/v1/projects/" + projectId + "/llm-usage");
        if (!bffToken.hasRole("ADMIN")) {
            projectService.assertMemberAccess(projectId, userId);
        }

        Instant fromTs = parseInstant(from);
        Instant toTs   = parseInstant(to);

        ProjectLlmUsageResponse response = service.forProject(projectId, fromTs, toTs);
        return Response.ok(ApiResponse.success(response)).build();
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO date: " + iso);
        }
    }
}