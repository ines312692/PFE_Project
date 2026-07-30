package com.elyadata.feature.project.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.project.domain.model.InvitationStatus;
import com.elyadata.feature.project.domain.model.ProjectInvitation;
import com.elyadata.feature.project.domain.model.ProjectRole;
import com.elyadata.feature.project.domain.service.ProjectInvitationService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvitationControllerIT {

    @InjectMock ProjectInvitationService invitationService;
    @InjectMock BffTokenService bffToken;

    private static final UUID USER_ID       = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INVITER_ID    = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID INVITATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PROJECT_ID    = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final String BASE        = "/v1/invitations";

    private RequestSpecification spec;

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        spec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private ProjectInvitation anInvitation(InvitationStatus status) {
        return ProjectInvitation.builder()
                .id(INVITATION_ID).projectId(PROJECT_ID).projectName("Test Project")
                .invitedBy(INVITER_ID).invitedByUsername("inviter")
                .invitedUser(USER_ID).role(ProjectRole.VIEWER)
                .status(status)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(86400))
                .build();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/invitations/me - returns 200 with pending invitations")
    void getMyInvitations_returns200() {
        when(invitationService.getPendingForUser(USER_ID)).thenReturn(List.of(anInvitation(InvitationStatus.PENDING)));

        given().spec(spec)
                .when().get(BASE + "/me")
                .then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].status", equalTo("PENDING"))
                .body("data[0].projectName", equalTo("Test Project"));
    }

    @Test @Order(2)
    @DisplayName("GET /v1/invitations/me - returns empty list when no pending")
    void getMyInvitations_empty_returns200() {
        when(invitationService.getPendingForUser(USER_ID)).thenReturn(List.of());

        given().spec(spec)
                .when().get(BASE + "/me")
                .then().statusCode(200)
                .body("data", hasSize(0));
    }

    @Test @Order(3)
    @DisplayName("POST /v1/invitations/{id}/respond - accept returns 200")
    void respond_accept_returns200() {
        when(invitationService.accept(eq(INVITATION_ID), eq(USER_ID)))
                .thenReturn(anInvitation(InvitationStatus.ACCEPTED));

        given().spec(spec)
                .body("{\"action\": \"ACCEPT\"}")
                .when().post(BASE + "/" + INVITATION_ID + "/respond")
                .then().statusCode(200)
                .body("data.status", equalTo("ACCEPTED"));

        verify(invitationService).accept(INVITATION_ID, USER_ID);
    }

    @Test @Order(4)
    @DisplayName("POST /v1/invitations/{id}/respond - decline returns 200")
    void respond_decline_returns200() {
        when(invitationService.decline(eq(INVITATION_ID), eq(USER_ID)))
                .thenReturn(anInvitation(InvitationStatus.DECLINED));

        given().spec(spec)
                .body("{\"action\": \"DECLINE\"}")
                .when().post(BASE + "/" + INVITATION_ID + "/respond")
                .then().statusCode(200)
                .body("data.status", equalTo("DECLINED"));

        verify(invitationService).decline(INVITATION_ID, USER_ID);
    }

    @Test @Order(5)
    @DisplayName("POST /v1/invitations/{id}/respond - returns 400 when action is blank")
    void respond_blankAction_returns400() {
        given().spec(spec)
                .body("{\"action\": \"\"}")
                .when().post(BASE + "/" + INVITATION_ID + "/respond")
                .then().statusCode(400);
    }
}