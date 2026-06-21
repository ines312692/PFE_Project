package com.elyadata.feature.proxy.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.common.pagination.Page;
import com.elyadata.common.utils.PageParam;
import com.elyadata.feature.proxy.application.dto.*;
import com.elyadata.feature.proxy.application.mapper.ProxyRestMapper;
import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;
import com.elyadata.feature.proxy.domain.service.ProxyService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/v1/proxies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProxyController {

    private final ProxyService proxyService;
    private final ProxyRestMapper mapper;

    @Inject
    public ProxyController(ProxyService proxyService, ProxyRestMapper mapper) {
        this.proxyService = proxyService;
        this.mapper = mapper;
    }

    @GET
    public Response list(
            @BeanParam @Valid PageParam pageParam,
            @QueryParam("search") String search,
            @QueryParam("status") ProxyStatus status,
            @QueryParam("protocol") ProxyProtocol protocol,
            @QueryParam("source") ProxySource source,
            @QueryParam("antiBotProvider") AntiBotProvider antiBotProvider,
            @QueryParam("antiBotStatus") ProtectionStatus antiBotStatus,
            @QueryParam("sortBy") @DefaultValue("createdAt") String sortBy,
            @QueryParam("sortOrder") @DefaultValue("desc") String sortOrder
    ) {
        ProxySearchCriteria criteria = ProxySearchCriteria.builder()
                .search(search).status(status).protocol(protocol).source(source)
                .antiBotProvider(antiBotProvider).antiBotStatus(antiBotStatus)
                .sortBy(sortBy).sortOrder(sortOrder)
                .build();

        Page<Proxy> result = proxyService.findAll(criteria, pageParam);
        List<UUID> ids = result.content().stream().map(Proxy::id).toList();
        Map<UUID, List<ProxyAntiBotStatus>> grouped =
                proxyService.getAntiBotStatusesGrouped(ids);

        List<ProxyResponse> mapped = mapper.toRest(result.content(), grouped);
        Page<ProxyResponse> pageResp = new Page<>(
                mapped, result.totalElements(), result.page(), result.size());

        return Response.ok(ApiResponse.success(pageResp)).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        Proxy proxy = proxyService.findById(id);
        List<ProxyAntiBotStatus> statuses = proxyService.getAntiBotStatuses(id);
        return Response.ok(ApiResponse.success(mapper.toRest(proxy, statuses))).build();
    }

    @GET
    @Path("/stats")
    public Response stats() {
        ProxyStatsResponse data = mapper.toStats(proxyService.stats());
        return Response.ok(ApiResponse.success(data)).build();
    }


    @POST
    public Response create(@Valid CreateProxyRequest req) {
        Proxy created = proxyService.createManual(
                req.host(), req.port(), req.protocol(),
                req.username(), req.password(),
                req.country(), req.anonymity()
        );
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(mapper.toRest(created)))
                .build();
    }

    @POST
    @Path("/refresh")
    public Response refresh() {
        int added = proxyService.refreshPool();
        return Response.ok(ApiResponse.success(
                new ProxyRefreshResponse(added, System.currentTimeMillis()))).build();
    }

    @POST
    @Path("/{id}/blacklist")
    public Response blacklist(@PathParam("id") UUID id) {
        proxyService.blacklist(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/reactivate")
    public Response reactivate(@PathParam("id") UUID id) {
        proxyService.reactivate(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/health-check")
    public Response triggerHealthCheck(@PathParam("id") UUID id) {
        ProxyHealthCheck result = proxyService.triggerHealthCheck(id);
        return Response.ok(ApiResponse.success(result)).build();
    }

    @POST
    @Path("/{id}/antibot-check")
    public Response triggerAntiBotCheck(
            @PathParam("id") UUID id,
            @QueryParam("provider") AntiBotProvider provider) {
        if (provider == null) {
            ProxyHealthCheck result = proxyService.triggerFullAntiBotCheck(id);
            return Response.ok(ApiResponse.success(result)).build();
        }
        DetectionResult result = proxyService.triggerSingleAntiBotCheck(id, provider);
        return Response.ok(ApiResponse.success(result)).build();
    }

    @GET
    @Path("/{id}/antibot-statuses")
    public Response getAntiBotStatuses(@PathParam("id") UUID id) {
        return Response.ok(ApiResponse.success(
                proxyService.getAntiBotStatuses(id))).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        proxyService.delete(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/antibot-providers")
    public Response antiBotProviders() {
        return Response.ok(ApiResponse.success(
                proxyService.registeredAntiBotProviders())).build();
    }

    @GET
    @Path("/dashboard")
    public Response dashboard() {
        ProxyDashboard data = proxyService.dashboard();
        return Response.ok(ApiResponse.success(mapper.toDashboard(data))).build();
    }

    @GET
    @Path("/countries")
    public Response availableCountries() {
        List<CountryStatDto> data = proxyService.availableCountries().stream()
                .map(c -> new CountryStatDto(c.code(), c.count()))
                .toList();
        return Response.ok(ApiResponse.success(data)).build();
    }
}