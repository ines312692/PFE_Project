package com.elyadata.feature.notification.domain.service;

import com.elyadata.feature.notification.domain.model.Notification;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.port.NotificationPort;
import com.elyadata.feature.notification.infrastructure.sse.NotificationBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationPort notificationPort;
    private final NotificationBroadcaster broadcaster;

    @Override
    @Transactional
    public void notify(UUID userId, UUID jobId, NotificationType type, String title, String message) {
        Notification notification = notificationPort.save(Notification.builder()
                .userId(userId)
                .jobId(jobId)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .build());

        log.info("[NOTIF] Created type={} for user={} job={}", type, userId, jobId);
        broadcaster.send(userId, notification);
    }

    @Override
    public List<Notification> getUserNotifications(UUID userId) {
        return notificationPort.findByUserId(userId);
    }

    @Override
    public long getUnreadCount(UUID userId) {
        return notificationPort.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId) {
        notificationPort.markAsRead(notificationId);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationPort.markAllAsReadByUserId(userId);
        log.debug("[NOTIF] Marked all as read for user={}", userId);
    }

    @Override
    @Transactional
    public void deleteNotification(UUID id, UUID userId) {
        notificationPort.deleteByIdAndUserId(id, userId);
        log.debug("[NOTIF] Deleted notification={} for user={}", id, userId);
    }

    @Override
    @Transactional
    public void deleteAllNotifications(UUID userId) {
        notificationPort.deleteAllByUserId(userId);
        log.debug("[NOTIF] Deleted all notifications for user={}", userId);
    }

    @Override
    @Transactional
    public void createAndBroadcast(UUID userId, NotificationType type,
                                   String title, String message, String referenceId) {
        Notification notification = notificationPort.save(Notification.builder()
                .userId(userId)
                .jobId(null)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .referenceId(referenceId)
                .build());

        log.info("[NOTIF] Created type={} for user={} referenceId={}", type, userId, referenceId);
        broadcaster.send(userId, notification);
    }
}