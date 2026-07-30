package com.elyadata.feature.notification.application.controller;

import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.notification.domain.model.Notification;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.notification.infrastructure.sse.NotificationBroadcaster;
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
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationControllerIT {

    @InjectMock NotificationService notificationService;
    @InjectMock NotificationBroadcaster broadcaster;
    @InjectMock BffTokenService bffToken;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NOTIF_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID JOB_ID   = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BASE   = "/v1/notifications";

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

    private Notification aNotification() {
        return Notification.builder()
                .id(NOTIF_ID).userId(USER_ID).jobId(JOB_ID)
                .type(NotificationType.JOB_COMPLETED)
                .title("Job done").message("Your job finished successfully")
                .read(false).createdAt(Instant.now())
                .build();
    }

    @Test @Order(1)
    @DisplayName("GET /v1/notifications - returns 200 with user notifications")
    void getNotifications_returns200() {
        when(notificationService.getUserNotifications(USER_ID)).thenReturn(List.of(aNotification()));

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].type", equalTo("JOB_COMPLETED"));
    }

    @Test @Order(2)
    @DisplayName("GET /v1/notifications - empty list returns 200")
    void getNotifications_empty_returns200() {
        when(notificationService.getUserNotifications(USER_ID)).thenReturn(List.of());

        given().spec(spec)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data", hasSize(0));
    }

    @Test @Order(3)
    @DisplayName("GET /v1/notifications/unread-count - returns 200 with count")
    void getUnreadCount_returns200() {
        when(notificationService.getUnreadCount(USER_ID)).thenReturn(3L);

        given().spec(spec)
                .when().get(BASE + "/unread-count")
                .then().statusCode(200)
                .body("data", equalTo(3));
    }

    @Test @Order(4)
    @DisplayName("PATCH /v1/notifications/{id}/read - returns 200")
    void markAsRead_returns200() {
        doNothing().when(notificationService).markAsRead(NOTIF_ID);

        given().spec(spec)
                .when().patch(BASE + "/" + NOTIF_ID + "/read")
                .then().statusCode(200)
                .body("data", containsString("Marked as read"));

        verify(notificationService).markAsRead(NOTIF_ID);
    }

    @Test @Order(5)
    @DisplayName("PATCH /v1/notifications/read-all - returns 200")
    void markAllAsRead_returns200() {
        doNothing().when(notificationService).markAllAsRead(USER_ID);

        given().spec(spec)
                .when().patch(BASE + "/read-all")
                .then().statusCode(200)
                .body("data", containsString("All marked as read"));

        verify(notificationService).markAllAsRead(USER_ID);
    }

    @Test @Order(6)
    @DisplayName("DELETE /v1/notifications/{id} - returns 200")
    void deleteNotification_returns200() {
        doNothing().when(notificationService).deleteNotification(NOTIF_ID, USER_ID);

        given().spec(spec)
                .when().delete(BASE + "/" + NOTIF_ID)
                .then().statusCode(200)
                .body("data", containsString("Notification deleted"));

        verify(notificationService).deleteNotification(NOTIF_ID, USER_ID);
    }

    @Test @Order(7)
    @DisplayName("DELETE /v1/notifications - returns 200 deletes all")
    void deleteAllNotifications_returns200() {
        doNothing().when(notificationService).deleteAllNotifications(USER_ID);

        given().spec(spec)
                .when().delete(BASE)
                .then().statusCode(200)
                .body("data", containsString("All notifications deleted"));

        verify(notificationService).deleteAllNotifications(USER_ID);
    }
}
