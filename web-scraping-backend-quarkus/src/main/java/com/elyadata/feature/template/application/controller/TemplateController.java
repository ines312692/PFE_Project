package com.elyadata.feature.template.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.template.application.mapper.TemplateRestMapper;
import com.elyadata.feature.template.domain.service.TemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Path("/v1/templates")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService    templateService;
    private final TemplateRestMapper templateRestMapper;

    @GET
    public Response getAll() {
        return Response.ok(
                        ApiResponse.success(
                                templateRestMapper.toResponseList(templateService.getAll())))
                .build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        return Response.ok(
                        ApiResponse.success(
                                templateRestMapper.toResponse(templateService.getById(id))))
                .build();
    }
}