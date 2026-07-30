package com.elyadata.feature.user.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.user.application.dto.CreateUserRequest;
import com.elyadata.feature.user.application.dto.UpdateUserRequest;
import com.elyadata.feature.user.application.mapper.UserRestMapper;
import com.elyadata.feature.user.domain.service.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@Path("/v1/users")
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserController {

    private final UserService     userService;
    private final UserRestMapper  userRestMapper;
    private final BffTokenService bffToken;

    @POST
    public Response createUser(@Valid CreateUserRequest request) {
        requireAdmin();
        UUID adminId = bffToken.resolveCurrentUserId("/v1/users");
        var created = userRestMapper.toRest(
                userService.createUser(userRestMapper.toDomain(request), adminId));
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.created(created))
                .build();
    }

    @GET
    public Response getAllUsers() {
        requireAdmin();
        return Response.ok(ApiResponse.success(
                userRestMapper.toRest(userService.getAllUsers()))).build();
    }

    @GET
    @Path("/{id}")
    public Response getUserById(@PathParam("id") UUID id) {
        requireAdmin();
        return Response.ok(ApiResponse.success(
                userRestMapper.toRest(userService.getUserById(id)))).build();
    }

    @PATCH
    @Path("/{id}")
    public Response updateUser(@PathParam("id") UUID id, @Valid UpdateUserRequest request) {
        requireAdmin();
        UUID adminId = bffToken.resolveCurrentUserId("/v1/users/" + id);
        var updated = userRestMapper.toRest(
                userService.updateUser(id, userRestMapper.toDomain(request), adminId));
        return Response.ok(ApiResponse.success(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") UUID id) {
        requireAdmin();
        userService.deleteUser(id);
        return Response.noContent().build();
    }

    private void requireAdmin() {
        if (!bffToken.hasRole("ADMIN")) {
            throw new WebApplicationException("Forbidden — ADMIN role required",
                    Response.Status.FORBIDDEN);
        }
    }
}