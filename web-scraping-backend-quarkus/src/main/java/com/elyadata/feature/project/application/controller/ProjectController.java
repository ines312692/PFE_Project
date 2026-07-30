package com.elyadata.feature.project.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.dto.JobResponse;
import com.elyadata.feature.job.application.mapper.JobRestMapper;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.project.application.dto.*;
import com.elyadata.feature.project.application.mapper.ProjectRestMapper;
import com.elyadata.feature.project.domain.model.Project;
import com.elyadata.feature.project.domain.model.ProjectInvitation;
import com.elyadata.feature.project.domain.model.ProjectMember;
import com.elyadata.feature.project.domain.model.ProjectRole;
import com.elyadata.feature.project.domain.service.ProjectInvitationService;
import com.elyadata.feature.project.domain.service.ProjectService;
import com.elyadata.feature.project.domain.service.port.ProjectMemberPort;
import com.elyadata.feature.scraperesult.domain.service.ProjectScrapeStatsService;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.service.port.JobReadPort;
import com.elyadata.feature.project.domain.model.ProjectSearchCriteria;
import com.elyadata.feature.project.domain.model.ProjectStats;
import com.elyadata.feature.project.domain.model.ProjectStatus;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import java.time.Instant;
import java.util.HashSet;


import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/v1/projects")
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectController {

    private static final String PROJECTS_PATH = "/v1/projects";

    private final ProjectService projectService;
    private final ProjectRestMapper mapper;
    private final JobService jobService;
    private final JobRestMapper jobMapper;
    private final UserPort userPort;
    private final BffTokenService bffToken;
    private final ProjectInvitationService invitationService;
    private final ProjectMemberPort memberPort;
    private final ProjectScrapeStatsService scrapeStatsService;

    private final JobReadPort jobReadPort;

    @POST
    public Response createProject(@Valid CreateProjectRequest request) {
        UUID userId = bffToken.resolveCurrentUserId(PROJECTS_PATH);
        Project created = projectService.createProject(mapper.toDomain(request), userId);
        ProjectResponse response = enrichResponse(mapper.toRest(created));
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.created(response)).build();
    }

    @GET
    public Response searchProjects(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("search") String search,
            @QueryParam("status") ProjectStatus status,
            @QueryParam("createdFrom") String createdFromIso,
            @QueryParam("createdTo") String createdToIso,
            @QueryParam("sortBy") @DefaultValue("createdAt") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = bffToken.resolveCurrentUserId(PROJECTS_PATH);
        int safeSize = Math.clamp(size, 1, 100);
        int safePage = Math.max(page, 0);

        ProjectSearchCriteria criteria = ProjectSearchCriteria.builder()
                .memberId(isAdmin() ? null : userId)
                .search(search)
                .status(status)
                .createdFrom(parseInstant(createdFromIso))
                .createdTo(parseInstant(createdToIso))
                .sortField(parseSortField(sortBy))
                .sortDirection(parseSortDirection(sortDirection))
                .build();

        Page<Project> result = projectService.search(criteria, safePage, safeSize);
        List<ProjectResponse> enriched = enrichBatch(mapper.toRest(result.content()));

        return Response.ok(ApiResponse.paginated(
                enriched, result.totalElements(), result.page(), result.size())).build();
    }

    @GET
    @Path("/stats")
    public Response getStats() {
        UUID userId = bffToken.resolveCurrentUserId(PROJECTS_PATH + "/stats");
        UUID scopedMember = isAdmin() ? null : userId;
        ProjectStats stats = projectService.getStats(scopedMember);
        return Response.ok(ApiResponse.success(stats)).build();
    }

    @GET
    @Path("/{id}")
    public Response getProject(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMemberAccess(id, userId);
        ProjectResponse response = enrichResponse(mapper.toRest(projectService.getProjectById(id)));
        return Response.ok(ApiResponse.success(response)).build();
    }

    @PATCH
    @Path("/{id}")
    public Response updateProject(@PathParam("id") UUID id, @Valid UpdateProjectRequest request) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMinimumRole(id, userId, ProjectRole.MEMBER);
        Project updated = projectService.updateProject(id, mapper.toDomain(request), userId);
        return Response.ok(ApiResponse.success(enrichResponse(mapper.toRest(updated)))).build();
    }

    @POST
    @Path("/{id}/archive")
    public Response archiveProject(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMinimumRole(id, userId, ProjectRole.OWNER);
        projectService.archiveProject(id, userId);
        return Response.ok(ApiResponse.success(Map.of("message", "Project archived."))).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteProject(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMinimumRole(id, userId, ProjectRole.OWNER);
        projectService.deleteProject(id, userId);
        return Response.noContent().build();
    }


    @GET
    @Path("/{id}/members")
    public Response getMembers(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMemberAccess(id, userId);
        List<ProjectMemberResponse> members = mapper.toRestMembers(
                projectService.getProjectMembers(id));
        return Response.ok(ApiResponse.success(members)).build();
    }

    @POST
    @Path("/{id}/members")
    public Response addMember(@PathParam("id") UUID id, @Valid AddMemberRequest request) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMinimumRole(id, userId, ProjectRole.OWNER);
        ProjectMember added = projectService.addMember(id, request.userId(), request.role(), userId);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.created(mapper.toRest(added))).build();
    }

    @PATCH
    @Path("/{id}/members/{userId}")
    public Response updateMemberRole(
            @PathParam("id") UUID id,
            @PathParam("userId") UUID targetUserId,
            @Valid UpdateMemberRoleRequest request) {
        UUID callerId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMinimumRole(id, callerId, ProjectRole.OWNER);
        ProjectMember updated = projectService.updateMemberRole(id, targetUserId, request.role(), callerId);
        return Response.ok(ApiResponse.success(mapper.toRest(updated))).build();
    }

    @DELETE
    @Path("/{id}/members/{userId}")
    public Response removeMember(
            @PathParam("id") UUID id,
            @PathParam("userId") UUID targetUserId) {
        UUID callerId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMinimumRole(id, callerId, ProjectRole.OWNER);
        projectService.removeMember(id, targetUserId, callerId);
        return Response.noContent().build();
    }


    @GET
    @Path("/{id}/jobs")
    public Response getProjectJobs(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id));
        if (!isAdmin()) projectService.assertMemberAccess(id, userId);
        List<JobResponse> jobs = jobMapper.toRest(jobService.getJobsByProjectId(id));
        return Response.ok(ApiResponse.success(jobs)).build();
    }

    private boolean isAdmin() {
        return bffToken.hasRole("ADMIN");
    }

    private String projectPath(UUID id) {
        return PROJECTS_PATH + "/" + id;
    }

    private List<ProjectResponse> enrichBatch(List<ProjectResponse> responses) {
        if (responses.isEmpty()) return List.of();

        Set<UUID> ownerIds = responses.stream()
                .map(ProjectResponse::ownerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> projectIds = responses.stream()
                .map(ProjectResponse::id)
                .collect(Collectors.toCollection(HashSet::new));
        Map<UUID, String> usernames = userPort.findAllByIds(ownerIds).stream()
                .collect(Collectors.toMap(User::id, User::username));
        Map<UUID, Long> memberCounts = memberCountByProjectIds(projectIds);
        Map<UUID, Long> jobCounts = jobReadPort.countByProjectIds(projectIds);

        return responses.stream()
                .map(r -> r.toBuilder()
                        .ownerUsername(usernames.getOrDefault(r.ownerId(), "—"))
                        .memberCount(memberCounts.getOrDefault(r.id(), 0L).intValue())
                        .jobCount(jobCounts.getOrDefault(r.id(), 0L).intValue())
                        .build())
                .toList();
    }

    private ProjectResponse enrichResponse(ProjectResponse response) {
        return enrichBatch(List.of(response)).getFirst();
    }

    private Map<UUID, Long> memberCountByProjectIds(Set<UUID> projectIds) {
        return memberPort.countByProjectIds(projectIds);
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO date: " + iso);
        }
    }

    private static ProjectSearchCriteria.SortField parseSortField(String s) {
        return switch (s == null ? "createdAt" : s.toLowerCase()) {
            case "updatedat" -> ProjectSearchCriteria.SortField.UPDATED_AT;
            case "name" -> ProjectSearchCriteria.SortField.NAME;
            default -> ProjectSearchCriteria.SortField.CREATED_AT;
        };
    }

    private static ProjectSearchCriteria.SortDirection parseSortDirection(String s) {
        return "asc".equalsIgnoreCase(s)
                ? ProjectSearchCriteria.SortDirection.ASC
                : ProjectSearchCriteria.SortDirection.DESC;
    }

    @POST
    @Path("/{id}/invitations")
    public Response sendInvitation(@PathParam("id") UUID id,
                                   @Valid SendInvitationRequest request) {
        UUID callerId = bffToken.resolveCurrentUserId(projectPath(id) + "/invitations");
        if (!isAdmin()) projectService.assertMinimumRole(id, callerId, ProjectRole.OWNER);
        ProjectInvitation inv = invitationService.send(id, callerId, request.email(), request.role());
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.created(toInvitationResponse(inv))).build();
    }

    @GET
    @Path("/{id}/invitations")
    public Response getProjectInvitations(@PathParam("id") UUID id) {
        UUID callerId = bffToken.resolveCurrentUserId(projectPath(id) + "/invitations");
        if (!isAdmin()) projectService.assertMemberAccess(id, callerId);
        List<InvitationResponse> list = invitationService.getPendingForProject(id)
                .stream().map(this::toInvitationResponse).toList();
        return Response.ok(ApiResponse.success(list)).build();
    }

    private InvitationResponse toInvitationResponse(ProjectInvitation inv) {
        return new InvitationResponse(
                inv.id(), inv.projectId(), inv.projectName(),
                inv.invitedBy(), inv.invitedByUsername(),
                inv.role(), inv.status(),
                inv.createdAt(), inv.expiresAt()
        );
    }

    @GET
    @Path("/{id}/scrape-stats")
    public Response getScrapeStats(
            @PathParam("id") UUID id,
            @QueryParam("from") String fromIso,
            @QueryParam("to") String toIso) {
        UUID userId = bffToken.resolveCurrentUserId(projectPath(id) + "/scrape-stats");
        if (!isAdmin()) projectService.assertMemberAccess(id, userId);
        var stats = scrapeStatsService.forProject(id, parseInstant(fromIso), parseInstant(toIso));
        return Response.ok(ApiResponse.success(stats)).build();
    }
}