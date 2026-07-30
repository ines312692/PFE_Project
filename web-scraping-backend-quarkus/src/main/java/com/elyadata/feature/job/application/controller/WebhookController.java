package com.elyadata.feature.job.application.controller;

import com.elyadata.feature.job.domain.service.webhook.WebhookTriggerService;
import com.elyadata.feature.job.domain.service.webhook.WebhookTriggerService.Outcome;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;


@Slf4j
@ApplicationScoped
@Path("/v1/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookTriggerService triggerService;

    @POST
    @Path("/jobs/{token}")
    @Consumes(MediaType.WILDCARD)  // body is opaque bytes — content-type is irrelevant
    public Response trigger(@PathParam("token") String token,
                            @HeaderParam("X-Webhook-Timestamp") String timestampHdr,
                            @HeaderParam("X-Webhook-Signature") String signatureHdr,
                            byte[] body,
                            @Context HttpHeaders headers,
                            @Context HttpServerRequest request) {

        String sourceIp = resolveClientIp(headers, request);
        String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);

        Outcome outcome = triggerService.process(
                token, body, timestampHdr, signatureHdr, sourceIp, userAgent);

        return Response.status(outcome.httpStatus())
                .entity(Map.of(
                        "status", outcome.status().name(),
                        "message", outcome.message()))
                .build();
    }

    private static String resolveClientIp(HttpHeaders headers, HttpServerRequest request) {
        String xff = headers.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        if (request != null && request.remoteAddress() != null) {
            return request.remoteAddress().host();
        }
        return null;
    }
}