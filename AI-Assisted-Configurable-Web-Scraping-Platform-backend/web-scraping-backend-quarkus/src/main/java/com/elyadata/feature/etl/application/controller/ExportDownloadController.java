package com.elyadata.feature.etl.application.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/v1/exports")
@Produces(MediaType.APPLICATION_JSON)
public class ExportDownloadController {


    private final ExportStorageService storageService;

    public ExportDownloadController(ExportStorageService storageService) {
        this.storageService = storageService;
    }

    @GET
    @Path("/{exportId}/download")
    @Produces(MediaType.WILDCARD)
    public Response download(@PathParam("exportId") String exportId) {
        log.info("[EXPORT] Download requested for id={}", exportId);

        ExportStorageService.ExportEntry entry = storageService.get(exportId)
                .orElseThrow(() -> new NotFoundException(
                        "Export not found or expired. Exports are only available for 15 minutes."));

        return Response.ok(entry.content())
                .type(entry.contentType())
                .header("Content-Disposition",
                        "attachment; filename=\"" + entry.filename() + "\"")
                .header("Content-Length", entry.content().length)
                .header("Cache-Control", "no-store")
                .build();
    }
}