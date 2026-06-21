package com.elyadata.feature.visualselector.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.visualselector.application.dto.HardenSelectorRequest;
import com.elyadata.feature.visualselector.application.dto.HardenedSelectorResponse;
import com.elyadata.feature.visualselector.application.dto.SnapshotRequest;
import com.elyadata.feature.visualselector.application.dto.SnapshotResponse;
import com.elyadata.feature.visualselector.domain.model.HardenedSelector;
import com.elyadata.feature.visualselector.domain.service.SelectorHardener;
import com.elyadata.feature.visualselector.domain.service.SnapshotRenderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;


@ApplicationScoped
@Path(VisualSelectorController.BASE_PATH)
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VisualSelectorController {

    public static final String BASE_PATH = "/v1/visual-selector";

    private final SnapshotRenderService snapshotRenderService;
    private final SelectorHardener selectorHardener;

    @POST
    @Path("/snapshot")
    public Response snapshot(@Valid SnapshotRequest request) {
        SnapshotResponse result = snapshotRenderService.render(request);
        return Response.ok(ApiResponse.success(result)).build();
    }

    @POST
    @Path("/harden")
    public Response harden(@Valid HardenSelectorRequest request) {
        HardenedSelector hardened = selectorHardener.harden(request);
        HardenedSelectorResponse result = HardenedSelectorResponse.builder()
                .cssSelector(hardened.cssSelector())
                .xpath(hardened.xpath())
                .matchCount(hardened.matchCount())
                .unique(hardened.unique())
                .previewValues(hardened.previewValues())
                .suggestedName(hardened.suggestedName())
                .warning(hardened.warning())
                .build();
        return Response.ok(ApiResponse.success(result)).build();
    }
}