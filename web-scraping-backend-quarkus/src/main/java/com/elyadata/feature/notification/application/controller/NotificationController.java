package com.elyadata.feature.notification.application.controller;

import com.elyadata.common.api.ApiResponse;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.notification.application.dto.NotificationResponse;
import com.elyadata.feature.notification.domain.model.Notification;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.notification.infrastructure.sse.NotificationBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@Path("/v1/notifications")
@RequiredArgsConstructor
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationController {

    private static final String NOTIF_PATH = "/v1/notifications";

    private final NotificationService notificationService;
    private final NotificationBroadcaster broadcaster;
    private final BffTokenService bffToken;

    @GET
    public Response getNotifications() {
        UUID userId = bffToken.resolveCurrentUserId(NOTIF_PATH);
        List<NotificationResponse> notifications = notificationService.getUserNotifications(userId).stream()
                .map(this::toResponse)
                .toList();
        return Response.ok(ApiResponse.success(notifications)).build();
    }

    @GET
    @Path("/unread-count")
    public Response getUnreadCount() {
        UUID userId = bffToken.resolveCurrentUserId(NOTIF_PATH);
        long count = notificationService.getUnreadCount(userId);
        return Response.ok(ApiResponse.success(count)).build();
    }

    @PATCH
    @Path("/{id}/read")
    public Response markAsRead(@PathParam("id") UUID id) {
        bffToken.resolveCurrentUserId(NOTIF_PATH + "/" + id);
        notificationService.markAsRead(id);
        return Response.ok(ApiResponse.success("Marked as read")).build();
    }

    @PATCH
    @Path("/read-all")
    public Response markAllAsRead() {
        UUID userId = bffToken.resolveCurrentUserId(NOTIF_PATH);
        notificationService.markAllAsRead(userId);
        return Response.ok(ApiResponse.success("All marked as read")).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteNotification(@PathParam("id") UUID id) {
        UUID userId = bffToken.resolveCurrentUserId(NOTIF_PATH + "/" + id);
        notificationService.deleteNotification(id, userId);
        return Response.ok(ApiResponse.success("Notification deleted")).build();
    }

    @DELETE
    public Response deleteAllNotifications() {
        UUID userId = bffToken.resolveCurrentUserId(NOTIF_PATH);
        notificationService.deleteAllNotifications(userId);
        return Response.ok(ApiResponse.success("All notifications deleted")).build();
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void stream(@Context SseEventSink sink, @Context Sse sse) {
        UUID userId = bffToken.resolveCurrentUserId(NOTIF_PATH + "/stream");
        log.info("[SSE] User {} connected to notification stream", userId);
        broadcaster.register(userId, sink, sse);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.id())
                .userId(n.userId())
                .jobId(n.jobId())
                .type(n.type())
                .title(n.title())
                .message(n.message())
                .read(n.read())
                .createdAt(n.createdAt())
                .referenceId(n.referenceId())
                .build();
    }
}