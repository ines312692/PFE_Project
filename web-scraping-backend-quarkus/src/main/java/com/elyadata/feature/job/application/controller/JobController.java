package com.elyadata.feature.job.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.common.exceptions.SubError;
import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.dto.CreateJobRequest;
import com.elyadata.feature.job.application.dto.JobResponse;
import com.elyadata.feature.job.application.dto.JobStatsResponse;
import com.elyadata.feature.job.application.dto.ScrapeResultResponse;
import com.elyadata.feature.job.application.dto.UpdateJobRequest;
import com.elyadata.feature.job.application.mapper.JobRestMapper;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.domain.service.JobEnqueueService;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.job.domain.service.port.WebhookDeliveryPort;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/v1/jobs")
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobController {

    private static final String JOBS_PATH = "/v1/jobs";

    private final JobService jobService;
    private final JobRestMapper jobRestMapper;
    private final ScrapeResultPort scrapeResultPort;
    private final UserPort userPort;
    private final BffTokenService bffToken;
    private final WebhookDeliveryPort webhookDeliveryPort;
    private final JobEnqueueService jobEnqueueService;

    @ConfigProperty(name = "app.public-base-url", defaultValue = "http://localhost:8080")
    String publicBaseUrl;


    public record WebhookCredentialsResponse(
            String url,
            String token,
            String secret,
            boolean enabled
    ) {
    }

    public record WebhookInfoResponse(
            String url,
            String token,
            boolean enabled,
            boolean hasSecret
    ) {
    }

    public record WebhookDeliveryResponse(
            UUID id,
            UUID jobId,
            Instant receivedAt,
            WebhookDeliveryStatus status,
            int httpStatus,
            String sourceIp,
            String userAgent,
            boolean signatureValid,
            String errorMessage,
            Integer durationMs
    ) {
    }

    @POST
    public Response createJob(@Valid CreateJobRequest request) {
        UUID userId = bffToken.resolveCurrentUserId(JOBS_PATH);
        JobResponse created = toResponseWithUsername(
                jobRestMapper.toRestWithSecret(jobService.createJob(jobRestMapper.toDomain(request), userId)));
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.created(created)).build();
    }

    @GET
    public Response searchJobs(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("search") String search,
            @QueryParam("status") JobStatus status,
            @QueryParam("priority") JobPriority priority,
            @QueryParam("triggerType") JobTriggerType triggerType,
            @QueryParam("jobType") JobType jobType,
            @QueryParam("createdFrom") String createdFromIso,
            @QueryParam("createdTo") String createdToIso,
            @QueryParam("sortBy") @DefaultValue("createdAt") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = bffToken.resolveCurrentUserId(JOBS_PATH);
        int safeSize = Math.clamp(size, 1, 100);
        int safePage = Math.max(page, 0);

        JobSearchCriteria criteria = JobSearchCriteria.builder()
                .ownerId(isAdmin() ? null : userId)
                .search(search)
                .status(status)
                .priority(priority)
                .triggerType(triggerType)
                .jobType(jobType)
                .createdFrom(parseInstant(createdFromIso))
                .createdTo(parseInstant(createdToIso))
                .sortField(parseSortField(sortBy))
                .sortDirection(parseSortDirection(sortDirection))
                .build();

        Page<Job> result = jobService.search(criteria, safePage, safeSize);
        List<JobResponse> jobs = toResponsesWithUsernames(jobRestMapper.toRest(result.content()));

        return Response.ok(ApiResponse.paginated(
                jobs, result.totalElements(), result.page(), result.size())).build();
    }

    @GET
    @Path("/stats")
    public Response getJobStats() {
        UUID userId = bffToken.resolveCurrentUserId(JOBS_PATH + "/stats");
        UUID scopedOwner = isAdmin() ? null : userId;
        JobStats stats = jobService.getStats(scopedOwner);
        return Response.ok(ApiResponse.success(JobStatsResponse.builder()
                .total(stats.total()).created(stats.created()).queued(stats.queued())
                .running(stats.running()).paused(stats.paused()).completed(stats.completed())
                .failed(stats.failed()).cancelled(stats.cancelled())
                .build())).build();
    }

    @GET
    @Path("/{id}")
    public Response getJobById(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        var job = isAdmin()
                ? jobRestMapper.toRest(jobService.getJobById(id))
                : jobRestMapper.toRest(jobService.getJobByIdAndUser(id, userId));
        return Response.ok(ApiResponse.success(toResponseWithUsername(job))).build();
    }

    @PATCH
    @Path("/{id}")
    public Response updateJob(@PathParam("id") UUID id, @Valid UpdateJobRequest request) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);
        JobResponse updated = toResponseWithUsername(
                jobRestMapper.toRest(jobService.updateJob(id, jobRestMapper.toDomain(request), userId)));
        return Response.ok(ApiResponse.success(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteJob(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);
        jobService.deleteJob(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/duplicate")
    public Response duplicateJob(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        JobResponse duplicated = toResponseWithUsername(
                jobRestMapper.toRest(jobService.duplicateJob(id, userId)));
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.created(duplicated)).build();
    }

    @POST
    @Path("/{id}/launch")
    public Response launchJob(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);

        boolean dispatched = jobEnqueueService.enqueue(id, "manual");
        if (!dispatched) {
            Job current = jobService.getJobById(id);
            throw DomainException.notValid(
                    "Job " + id + " cannot be launched from status " + current.status());
        }

        JobResponse job = toResponseWithUsername(
                jobRestMapper.toRest(jobService.getJobById(id)));
        return Response.ok(ApiResponse.success(job)).build();
    }

    @POST
    @Path("/{id}/pause")
    public Response pauseJob(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);
        JobResponse job = toResponseWithUsername(jobRestMapper.toRest(jobService.pauseJob(id)));
        return Response.ok(ApiResponse.success(job)).build();
    }

    @GET
    @Path("/{id}/results")
    public Response getScrapeResults(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);

        ScrapeResultResponse resp = scrapeResultPort.findByJobId(id)
                .map(this::toScrapeResponse)
                .orElseThrow(() -> DomainException.notFound(
                        "No scrape result for job: " + id,
                        jobPath(id) + "/results",
                        Set.of(SubError.builder()
                                .field("jobId")
                                .message("No result available yet for this job")
                                .rejectedValue(id.toString())
                                .errorCode("SCRAPE_RESULT_NOT_FOUND")
                                .build())));

        return Response.ok(ApiResponse.success(resp)).build();
    }

    @GET
    @Path("/{id}/history")
    public Response getJobHistory(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id));
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);

        List<ScrapeResultResponse> history = scrapeResultPort.findAllByJobId(id).stream()
                .map(this::toScrapeResponse)
                .toList();

        return Response.ok(ApiResponse.success(history)).build();
    }


    @GET
    @Path("/{id}/webhook")
    public Response getWebhookInfo(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id) + "/webhook");
        Job job = isAdmin() ? jobService.getJobById(id) : jobService.getJobByIdAndUser(id, userId);

        WebhookInfoResponse info = new WebhookInfoResponse(
                buildWebhookUrl(job.webhookToken()),
                job.webhookToken(),
                Boolean.TRUE.equals(job.webhookEnabled()),
                job.webhookSecret() != null && !job.webhookSecret().isBlank());
        return Response.ok(ApiResponse.success(info)).build();
    }

    @POST
    @Path("/{id}/webhook/rotate")
    public Response rotateWebhookSecret(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id) + "/webhook/rotate");
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);

        WebhookCredentials creds = jobService.rotateWebhookSecret(id);
        return Response.ok(ApiResponse.success(new WebhookCredentialsResponse(
                buildWebhookUrl(creds.token()),
                creds.token(),
                creds.secret(),
                creds.enabled()))).build();
    }

    @GET
    @Path("/{id}/webhook/deliveries")
    public Response getWebhookDeliveries(@PathParam("id") UUID id,
                                         @QueryParam("page") @DefaultValue("0") int page,
                                         @QueryParam("size") @DefaultValue("20") int size) {
        UUID userId = bffToken.resolveCurrentUserId(jobPath(id) + "/webhook/deliveries");
        if (!isAdmin()) jobService.getJobByIdAndUser(id, userId);

        int safeSize = Math.clamp(size, 1, 100);
        int safePage = Math.max(page, 0);

        Page<WebhookDelivery> result = webhookDeliveryPort.findByJobId(id, safePage, safeSize);
        List<WebhookDeliveryResponse> deliveries = result.content().stream()
                .map(d -> new WebhookDeliveryResponse(
                        d.id(), d.jobId(), d.receivedAt(), d.status(),
                        d.httpStatus(), d.sourceIp(), d.userAgent(),
                        d.signatureValid(), d.errorMessage(), d.durationMs()))
                .toList();

        return Response.ok(ApiResponse.paginated(
                deliveries, result.totalElements(), result.page(), result.size())).build();
    }


    private String buildWebhookUrl(String token) {
        if (token == null) return null;
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/v1/webhooks/jobs/" + token;
    }

    private boolean isAdmin() {
        return bffToken.hasRole("ADMIN");
    }

    private String jobPath(UUID id) {
        return JOBS_PATH + "/" + id;
    }

    private JobResponse toResponseWithUsername(JobResponse response) {
        if (response.createdBy() == null) return response;
        String username = userPort.findById(response.createdBy())
                .map(User::username)
                .orElse("—");
        return response.toBuilder().createdByUsername(username).build();
    }

    private List<JobResponse> toResponsesWithUsernames(List<JobResponse> responses) {
        Set<UUID> creatorIds = responses.stream()
                .map(JobResponse::createdBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> cache = userPort.findAll().stream()
                .filter(u -> creatorIds.contains(u.id()))
                .collect(Collectors.toMap(User::id, User::username));

        return responses.stream()
                .map(r -> r.createdBy() != null
                        ? r.toBuilder().createdByUsername(cache.getOrDefault(r.createdBy(), "—")).build()
                        : r)
                .toList();
    }

    private ScrapeResultResponse toScrapeResponse(ScrapeResult r) {
        return ScrapeResultResponse.builder()
                .id(r.id()).jobId(r.jobId()).status(r.status())
                .records(r.records()).errors(r.errors()).logs(r.logs())
                .pagesCrawled(r.pagesCrawled()).durationMs(r.durationMs())
                .engineUsed(r.engineUsed()).scrapedAt(r.scrapedAt())
                .discoveredFiles(r.discoveredFiles() != null ? r.discoveredFiles() : List.of())
                .build();
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO date: " + iso);
        }
    }

    private static JobSearchCriteria.SortField parseSortField(String s) {
        return switch (s == null ? "createdAt" : s.toLowerCase()) {
            case "updatedat" -> JobSearchCriteria.SortField.UPDATED_AT;
            case "name" -> JobSearchCriteria.SortField.NAME;
            default -> JobSearchCriteria.SortField.CREATED_AT;
        };
    }

    private static JobSearchCriteria.SortDirection parseSortDirection(String s) {
        return "asc".equalsIgnoreCase(s)
                ? JobSearchCriteria.SortDirection.ASC
                : JobSearchCriteria.SortDirection.DESC;
    }
}