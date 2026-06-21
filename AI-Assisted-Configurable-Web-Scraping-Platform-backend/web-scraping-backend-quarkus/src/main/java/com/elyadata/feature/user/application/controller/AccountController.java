package com.elyadata.feature.user.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.user.application.dto.UpdateAvatarRequest;
import com.elyadata.feature.user.application.mapper.UserRestMapper;
import com.elyadata.feature.user.domain.service.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@Path("/v1/account")
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountController {

    private final UserService userService;
    private final UserRestMapper userRestMapper;
    private final BffTokenService bffToken;

    @GET
    public Response getMyProfile() {
        var userId = bffToken.resolveCurrentUserId("/v1/account");
        var user = userRestMapper.toRest(userService.getUserById(userId));
        return Response.ok(ApiResponse.success(user)).build();
    }

    @PATCH
    @Path("/avatar")
    public Response updateMyAvatar(@Valid UpdateAvatarRequest request) {
        var userId = bffToken.resolveCurrentUserId("/v1/account/avatar");
        var updated = userRestMapper.toRest(
                userService.updateAvatar(userId, request.avatarUrl()));
        return Response.ok(ApiResponse.success(updated)).build();
    }
}