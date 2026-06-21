package com.elyadata.feature.schema.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.schema.application.dto.PromptAssistRequest;
import com.elyadata.feature.schema.application.dto.PromptAssistResponse;
import com.elyadata.feature.schema.application.dto.SchemaValidationRequest;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse;
import com.elyadata.feature.schema.domain.service.PromptAssistantService;
import com.elyadata.feature.schema.domain.service.SchemaValidatorService;
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
@Path(SchemaAssistController.ASSIST_PATH)
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchemaAssistController {

    public static final String ASSIST_PATH = "/v1/schema/assist";

    private final PromptAssistantService promptAssistantService;
    private final SchemaValidatorService schemaValidatorService;
    private final BffTokenService bffToken;

    @POST
    public Response assist(@Valid PromptAssistRequest request) {
        UUID userId = bffToken.resolveCurrentUserId(ASSIST_PATH);
        PromptAssistResponse result = promptAssistantService.assist(request, userId);
        return Response.ok(ApiResponse.success(result)).build();
    }

    @POST
    @Path("/validate")
    public Response validate(@Valid SchemaValidationRequest request) {
        SchemaValidationResponse result = schemaValidatorService.validate(request);
        return Response.ok(ApiResponse.success(result)).build();
    }
}