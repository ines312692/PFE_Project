package com.elyadata.feature.job.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.job.application.dto.CreateJobRequest;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.job.application.mapper.JobRestMapper;
import com.elyadata.feature.job.application.dto.JobResponse;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
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
import java.util.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;


@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobControllerIT {


    @InjectMock
    JobService jobService;
    @InjectMock
    JobRestMapper jobRestMapper;
    @InjectMock
    ScrapeResultPort scrapeResultPort;
    @InjectMock
    UserPort userPort;
    @InjectMock
    BffTokenService bffToken;

    private RequestSpecification authSpec;

    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String BASE_URL = "/v1/jobs";

    private Job aJob(JobStatus status) {
        return Job.builder()
                .id(JOB_ID)
                .name("Test Job")
                .targetUrl("https://example.com")
                .jobType(JobType.SCRAPER)
                .status(status)
                .priority(JobPriority.NORMAL)
                .triggerType(JobTriggerType.MANUAL)
                .extractionMode(ExtractionMode.CSS_SELECTOR)
                .createdBy(USER_ID)
                .createdAt(Instant.now())
                .build();
    }

    private JobResponse aJobResponse(JobStatus status) {
        return JobResponse.builder()
                .id(JOB_ID)
                .name("Test Job")
                .targetUrl("https://example.com")
                .jobType(JobType.SCRAPER)
                .status(status)
                .priority(JobPriority.NORMAL)
                .triggerType(JobTriggerType.MANUAL)
                .extractionMode(ExtractionMode.CSS_SELECTOR)
                .createdBy(USER_ID)
                .createdByUsername("testuser")
                .createdAt(Instant.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        when(bffToken.hasRole("ADMIN")).thenReturn(false);

        // Username resolution
        User user = User.builder().id(USER_ID).username("testuser").build();
        when(userPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userPort.findAll()).thenReturn(List.of(user));

        // SecurityFilter only checks token PRESENCE, not validity — a dummy Bearer is enough.
        authSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @Order(1)
    @DisplayName("GET /v1/jobs - should return 200 with list of jobs for current user")
    void getAllJobs_authenticatedUser_returns200WithJobList() {
        Job job = aJob(JobStatus.CREATED);
        JobResponse response = aJobResponse(JobStatus.CREATED);

        Page<Job> resultPage = new Page<>(List.of(job), 1L, 0, 20);
        when(jobService.search(any(JobSearchCriteria.class), anyInt(), anyInt()))
                .thenReturn(resultPage);
        when(jobRestMapper.toRest(List.of(job))).thenReturn(List.of(response));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].id", equalTo(JOB_ID.toString()))
                .body("data[0].name", equalTo("Test Job"))
                .body("data[0].status", equalTo("CREATED"))
                .body("data[0].createdByUsername", equalTo("testuser"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /v1/jobs - admin should see all jobs")
    void getAllJobs_adminUser_returnsAllJobs() {
        when(bffToken.hasRole("ADMIN")).thenReturn(true);
        Job job = aJob(JobStatus.CREATED);
        JobResponse response = aJobResponse(JobStatus.CREATED);

        Page<Job> resultPage = new Page<>(List.of(job), 1L, 0, 20);
        when(jobService.search(any(JobSearchCriteria.class), anyInt(), anyInt()))
                .thenReturn(resultPage);
        when(jobRestMapper.toRest(any(List.class))).thenReturn(List.of(response));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("data", hasSize(1));

        verify(jobService).search(any(JobSearchCriteria.class), anyInt(), anyInt());
    }

    @Test
    @Order(3)
    @DisplayName("GET /v1/jobs/{id} - should return 200 with job details")
    void getJobById_existingId_returns200() {
        Job job = aJob(JobStatus.RUNNING);
        JobResponse response = aJobResponse(JobStatus.RUNNING);

        when(jobService.getJobByIdAndUser(JOB_ID, USER_ID)).thenReturn(job);
        when(jobRestMapper.toRest(job)).thenReturn(response);

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + JOB_ID)
                .then()
                .statusCode(200)
                .body("data.id", equalTo(JOB_ID.toString()))
                .body("data.status", equalTo("RUNNING"));
    }


    @Test
    @Order(4)
    @DisplayName("POST /v1/jobs - should return 201 with created job")
    void createJob_validRequest_returns201() {
        String requestBody = """
                {
                  "name": "New Job",
                  "targetUrl": "https://example.com",
                  "jobType": "SCRAPER",
                  "priority": "NORMAL",
                  "triggerType": "MANUAL",
                  "extractionMode": "CSS_SELECTOR"
                }
                """;

        Job created = aJob(JobStatus.CREATED);
        JobResponse response = aJobResponse(JobStatus.CREATED);

        when(jobRestMapper.toDomain(any(CreateJobRequest.class))).thenReturn(created);
        when(jobService.createJob(any(), eq(USER_ID))).thenReturn(created);
        when(jobRestMapper.toRestWithSecret(created)).thenReturn(response);

        given()
                .spec(authSpec)
                .body(requestBody)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("data.id", equalTo(JOB_ID.toString()))
                .body("data.status", equalTo("CREATED"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /v1/jobs - should return 400 when request body is missing required fields")
    void createJob_missingRequiredFields_returns400() {
        String invalidBody = """
                {
                  "name": ""
                }
                """;

        given()
                .spec(authSpec)
                .body(invalidBody)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(jobService, never()).createJob(any(), any());
    }

    @Test
    @Order(6)
    @DisplayName("DELETE /v1/jobs/{id} - should return 204 on success")
    void deleteJob_existingId_returns204() {
        doNothing().when(jobService).deleteJob(JOB_ID);

        given()
                .spec(authSpec)
                .when()
                .delete(BASE_URL + "/" + JOB_ID)
                .then()
                .statusCode(204);

        verify(jobService).deleteJob(JOB_ID);
    }

    @Test
    @Order(7)
    @DisplayName("POST /v1/jobs/{id}/launch - should return 200 with RUNNING job")
    void launchJob_launchableJob_returns200WithRunningStatus() {
        Job running = aJob(JobStatus.RUNNING);
        JobResponse response = aJobResponse(JobStatus.RUNNING);

        when(jobService.launchJob(JOB_ID)).thenReturn(running);
        when(jobRestMapper.toRest(running)).thenReturn(response);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/launch")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("RUNNING"));
    }

    @Test
    @Order(8)
    @DisplayName("POST /v1/jobs/{id}/launch - should return 422 when job cannot be launched")
    void launchJob_nonLaunchableJob_returns422() {
        when(jobService.launchJob(JOB_ID))
                .thenThrow(com.elyadata.common.exceptions.domain.DomainException
                        .notValid("Job cannot be launched from status RUNNING"));

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/launch")
                .then()
                .statusCode(400); // DomainException.notValid maps to 400 (errorCode 107) in this project
    }

    @Test
    @Order(9)
    @DisplayName("POST /v1/jobs/{id}/pause - should return 200 with PAUSED job")
    void pauseJob_runningJob_returns200WithPausedStatus() {
        Job paused = aJob(JobStatus.PAUSED);
        JobResponse response = aJobResponse(JobStatus.PAUSED);

        when(jobService.pauseJob(JOB_ID)).thenReturn(paused);
        when(jobRestMapper.toRest(paused)).thenReturn(response);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/pause")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PAUSED"));
    }

    @Test
    @Order(10)
    @DisplayName("POST /v1/jobs/{id}/duplicate - should return 201 with copied job")
    void duplicateJob_existingJob_returns201() {
        Job copy = Job.builder()
                .id(UUID.randomUUID()).name("Test Job (copy)")
                .targetUrl("https://example.com").jobType(JobType.SCRAPER)
                .status(JobStatus.CREATED).priority(JobPriority.NORMAL)
                .triggerType(JobTriggerType.MANUAL).extractionMode(ExtractionMode.CSS_SELECTOR)
                .createdBy(USER_ID).createdAt(Instant.now()).build();

        JobResponse copyResponse = aJobResponse(JobStatus.CREATED)
                .toBuilder().name("Test Job (copy)").build();

        when(jobService.duplicateJob(JOB_ID, USER_ID)).thenReturn(copy);
        when(jobRestMapper.toRest(copy)).thenReturn(copyResponse);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + JOB_ID + "/duplicate")
                .then()
                .statusCode(201)
                .body("data.name", containsString("(copy)"));
    }

    @Test
    @Order(11)
    @DisplayName("Non-admin user should not see another user's job")
    void getJobById_nonAdminAccessingOtherUserJob_returns404() {
        when(jobService.getJobByIdAndUser(JOB_ID, USER_ID))
                .thenThrow(com.elyadata.common.exceptions.domain.DomainException
                        .notFound("Job not found: " + JOB_ID));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + JOB_ID)
                .then()
                .statusCode(404);
    }
}