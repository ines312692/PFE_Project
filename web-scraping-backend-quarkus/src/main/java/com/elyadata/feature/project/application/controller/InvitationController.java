package com.elyadata.feature.project.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.project.application.dto.InvitationResponse;
import com.elyadata.feature.project.application.dto.RespondInvitationRequest;
import com.elyadata.feature.project.domain.model.ProjectInvitation;
import com.elyadata.feature.project.domain.service.ProjectInvitationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/v1/invitations")
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvitationController {

    private static final String BASE = "/v1/invitations";

    private final ProjectInvitationService invitationService;
    private final BffTokenService bffToken;

    @GET
    @Path("/me")
    public Response getMyInvitations() {
        UUID currentUserId = bffToken.resolveCurrentUserId(BASE + "/me");
        List<InvitationResponse> list = invitationService.getPendingForUser(currentUserId)
                .stream().map(this::toResponse).toList();
        return Response.ok(ApiResponse.success(list)).build();
    }

    @POST
    @Path("/{invitationId}/respond")
    public Response respond(@PathParam("invitationId") UUID invitationId,
                            @Valid RespondInvitationRequest request) {
        UUID currentUserId = bffToken.resolveCurrentUserId(BASE + "/" + invitationId + "/respond");
        ProjectInvitation inv = "ACCEPT".equalsIgnoreCase(request.action())
                ? invitationService.accept(invitationId, currentUserId)
                : invitationService.decline(invitationId, currentUserId);
        return Response.ok(ApiResponse.success(toResponse(inv))).build();
    }

    private InvitationResponse toResponse(ProjectInvitation inv) {
        return new InvitationResponse(
                inv.id(), inv.projectId(), inv.projectName(),
                inv.invitedBy(), inv.invitedByUsername(),
                inv.role(), inv.status(),
                inv.createdAt(), inv.expiresAt()
        );
    }
}