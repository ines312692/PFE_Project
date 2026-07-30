package com.elyadata.feature.project.application.controller;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.mapper.JobRestMapper;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.job.domain.service.port.JobReadPort;
import com.elyadata.feature.project.application.dto.CreateProjectRequest;
import com.elyadata.feature.project.application.dto.ProjectResponse;
import com.elyadata.feature.project.application.mapper.ProjectRestMapper;
import com.elyadata.feature.project.domain.model.Project;
import com.elyadata.feature.project.domain.model.ProjectInvitation;
import com.elyadata.feature.project.domain.model.ProjectMember;
import com.elyadata.feature.project.domain.model.ProjectRole;
import com.elyadata.feature.project.domain.model.ProjectStats;
import com.elyadata.feature.project.domain.model.ProjectStatus;
import com.elyadata.feature.project.domain.service.ProjectInvitationService;
import com.elyadata.feature.project.domain.service.ProjectService;
import com.elyadata.feature.project.domain.service.port.ProjectMemberPort;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectControllerIT {

    @InjectMock
    ProjectService projectService;
    @InjectMock
    ProjectRestMapper mapper;
    @InjectMock
    JobService jobService;
    @InjectMock
    JobRestMapper jobMapper;
    @InjectMock
    UserPort userPort;
    @InjectMock
    BffTokenService bffToken;
    @InjectMock
    ProjectInvitationService invitationService;
    @InjectMock
    ProjectMemberPort memberPort;
    @InjectMock
    JobReadPort jobReadPort;

    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE_URL = "/v1/projects";

    private RequestSpecification authSpec;

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        when(bffToken.hasRole("ADMIN")).thenReturn(false);

        authSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private Project aProject() {
        return Project.builder()
                .id(PROJECT_ID)
                .name("Test Project")
                .description("A test project")
                .color("#FF0000")
                .status(ProjectStatus.ACTIVE)
                .createdBy(USER_ID)
                .createdAt(Instant.now())
                .build();
    }

    private ProjectResponse aProjectResponse() {
        return ProjectResponse.builder()
                .id(PROJECT_ID)
                .name("Test Project")
                .description("A test project")
                .color("#FF0000")
                .status(ProjectStatus.ACTIVE)
                .ownerId(USER_ID)
                .ownerUsername("testuser")
                .memberCount(1)
                .jobCount(0)
                .createdBy(USER_ID)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("POST /v1/projects - valid request returns 201 with created project")
    void createProject_validRequest_returns201() {
        Project created = aProject();
        ProjectResponse response = aProjectResponse();

        when(mapper.toDomain(any(CreateProjectRequest.class))).thenReturn(created);
        when(projectService.createProject(any(Project.class), any(UUID.class))).thenReturn(created);
        when(mapper.toRest(created)).thenReturn(response);
        when(userPort.findAllByIds(any())).thenReturn(List.of(
                User.builder().id(USER_ID).username("testuser").build()));
        when(memberPort.countByProjectIds(any())).thenReturn(Map.of(PROJECT_ID, 1L));
        when(jobReadPort.countByProjectIds(any())).thenReturn(Map.of(PROJECT_ID, 0L));

        given()
                .spec(authSpec)
                .body("""
                        {"name": "Test Project", "description": "A test project", "color": "#FF0000"}
                        """)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("data.id", equalTo(PROJECT_ID.toString()))
                .body("data.name", equalTo("Test Project"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /v1/projects - returns 200 with paged list")
    void searchProjects_returns200WithPagedList() {
        Project project = aProject();
        ProjectResponse response = aProjectResponse();

        Page<Project> page = new Page<>(List.of(project), 1L, 0, 20);
        when(projectService.search(any(), anyInt(), anyInt())).thenReturn(page);
        when(mapper.toRest(List.of(project))).thenReturn(List.of(response));
        when(userPort.findAllByIds(any())).thenReturn(List.of(
                User.builder().id(USER_ID).username("testuser").build()));
        when(memberPort.countByProjectIds(any())).thenReturn(Map.of(PROJECT_ID, 1L));
        when(jobReadPort.countByProjectIds(any())).thenReturn(Map.of(PROJECT_ID, 0L));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.totalElements", equalTo(1));
    }

    @Test
    @Order(3)
    @DisplayName("GET /v1/projects/{id} - returns 200 with project details")
    void getProject_existingId_returns200() {
        Project project = aProject();
        ProjectResponse response = aProjectResponse();

        doNothing().when(projectService).assertMemberAccess(PROJECT_ID, USER_ID);
        when(projectService.getProjectById(PROJECT_ID)).thenReturn(project);
        when(mapper.toRest(project)).thenReturn(response);
        when(userPort.findAllByIds(any())).thenReturn(List.of(
                User.builder().id(USER_ID).username("testuser").build()));
        when(memberPort.countByProjectIds(any())).thenReturn(Map.of(PROJECT_ID, 1L));
        when(jobReadPort.countByProjectIds(any())).thenReturn(Map.of(PROJECT_ID, 0L));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROJECT_ID)
                .then()
                .statusCode(200)
                .body("data.id", equalTo(PROJECT_ID.toString()));
    }

    @Test
    @Order(4)
    @DisplayName("GET /v1/projects/{id} - returns 404 when project not found")
    void getProject_notFound_returns404() {
        doNothing().when(projectService).assertMemberAccess(PROJECT_ID, USER_ID);
        when(projectService.getProjectById(PROJECT_ID))
                .thenThrow(DomainException.notFound("Project not found: " + PROJECT_ID));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROJECT_ID)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(5)
    @DisplayName("GET /v1/projects/stats - returns 200 with project statistics")
    void getStats_returns200() {
        ProjectStats stats = ProjectStats.builder().total(5L).active(3L).archived(2L).build();
        when(projectService.getStats(USER_ID)).thenReturn(stats);

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/stats")
                .then()
                .statusCode(200)
                .body("data", notNullValue());
    }

    @Test
    @Order(6)
    @DisplayName("DELETE /v1/projects/{id} - returns 204 on success")
    void deleteProject_returns204() {
        doNothing().when(projectService).assertMinimumRole(PROJECT_ID, USER_ID, ProjectRole.OWNER);
        doNothing().when(projectService).deleteProject(PROJECT_ID, USER_ID);

        given()
                .spec(authSpec)
                .when()
                .delete(BASE_URL + "/" + PROJECT_ID)
                .then()
                .statusCode(204);

        verify(projectService).deleteProject(PROJECT_ID, USER_ID);
    }

    @Test
    @Order(7)
    @DisplayName("POST /v1/projects/{id}/archive - returns 200 with message")
    void archiveProject_returns200() {
        doNothing().when(projectService).assertMinimumRole(PROJECT_ID, USER_ID, ProjectRole.OWNER);
        doNothing().when(projectService).archiveProject(PROJECT_ID, USER_ID);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + PROJECT_ID + "/archive")
                .then()
                .statusCode(200)
                .body("data.message", containsString("archived"));
    }

    @Test
    @Order(8)
    @DisplayName("GET /v1/projects/{id}/members - returns 200 with members list")
    void getMembers_returns200() {
        doNothing().when(projectService).assertMemberAccess(PROJECT_ID, USER_ID);
        when(projectService.getProjectMembers(PROJECT_ID)).thenReturn(List.of());
        when(mapper.toRestMembers(any())).thenReturn(List.of());

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROJECT_ID + "/members")
                .then()
                .statusCode(200)
                .body("data", hasSize(0));
    }

    @Test
    @Order(9)
    @DisplayName("GET /v1/projects/{id}/jobs - returns 200 with job list")
    void getProjectJobs_returns200() {
        doNothing().when(projectService).assertMemberAccess(PROJECT_ID, USER_ID);
        when(jobService.getJobsByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(jobMapper.toRest(any(List.class))).thenReturn(List.of());

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROJECT_ID + "/jobs")
                .then()
                .statusCode(200)
                .body("data", hasSize(0));
    }

    @Test
    @Order(10)
    @DisplayName("GET /v1/projects/{id}/invitations - returns 200 with pending invitations")
    void getProjectInvitations_returns200() {
        doNothing().when(projectService).assertMemberAccess(PROJECT_ID, USER_ID);
        when(invitationService.getPendingForProject(PROJECT_ID)).thenReturn(List.of());

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROJECT_ID + "/invitations")
                .then()
                .statusCode(200)
                .body("data", hasSize(0));
    }
}
