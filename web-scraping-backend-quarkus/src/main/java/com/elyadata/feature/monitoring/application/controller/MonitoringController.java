package com.elyadata.feature.monitoring.application.controller;

import com.elyadata.feature.monitoring.application.dto.SystemHealthDto;
import com.elyadata.feature.monitoring.domain.service.SystemHealthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;


@Path("/v1/monitoring")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringController {

    @Inject
    SystemHealthService systemHealthService;

    @GET
    @Path("/system-health")
    public SystemHealthDto systemHealth() {
        return systemHealthService.snapshot();
    }
}
