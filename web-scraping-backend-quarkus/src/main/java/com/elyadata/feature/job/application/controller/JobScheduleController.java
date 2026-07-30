package com.elyadata.feature.job.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.domain.service.JobScheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/v1/jobs/schedule")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class JobScheduleController {

    private final JobScheduler scheduler;
    private final BffTokenService bffToken;

    public record UpcomingRunResponse(UUID jobId, String jobName, Instant fireAt) {
    }

    public record NextRunsResponse(String cron, List<Instant> next) {
    }

    @GET
    @Path("/upcoming")
    public Response upcoming(@QueryParam("from") String fromIso,
                             @QueryParam("to") String toIso) {
        bffToken.resolveCurrentUserId("/v1/jobs/schedule/upcoming");
        Instant from = fromIso != null ? Instant.parse(fromIso) : Instant.now();
        Instant to = toIso != null ? Instant.parse(toIso) : from.plus(30, ChronoUnit.DAYS);

        List<UpcomingRunResponse> runs = scheduler.upcomingRunsBetween(from, to).stream()
                .map(r -> new UpcomingRunResponse(r.jobId(), r.jobName(), r.fireAt()))
                .toList();
        return Response.ok(ApiResponse.success(runs)).build();
    }

    @GET
    @Path("/preview")
    public Response preview(@QueryParam("cron") String cron,
                            @QueryParam("count") @DefaultValue("5") int count) {
        bffToken.resolveCurrentUserId("/v1/jobs/schedule/preview");
        List<Instant> next = scheduler.nextFireTimes(cron, Math.clamp(count, 1, 50));
        return Response.ok(ApiResponse.success(new NextRunsResponse(cron, next))).build();
    }
}