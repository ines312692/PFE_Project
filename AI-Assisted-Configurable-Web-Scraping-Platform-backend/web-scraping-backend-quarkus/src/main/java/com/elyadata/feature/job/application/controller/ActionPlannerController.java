package com.elyadata.feature.job.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.dto.ActionPlanRequest;
import com.elyadata.feature.job.application.dto.ActionPlanResponse;
import com.elyadata.feature.job.domain.service.planner.ActionPlannerService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@Path(ActionPlannerController.PLANNER_PATH)
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ActionPlannerController {

    public static final String PLANNER_PATH = "/v1/jobs/action-planner";

    private final ActionPlannerService actionPlannerService;
    private final BffTokenService bffToken;

    @POST
    public Response plan(@Valid ActionPlanRequest request) {
        UUID userId = bffToken.resolveCurrentUserId(PLANNER_PATH);
        ActionPlanResponse result = actionPlannerService.plan(request, userId);
        return Response.ok(ApiResponse.success(result)).build();
    }
}