package com.elyadata.feature.notification.domain.service;

import com.elyadata.feature.notification.domain.model.Notification;
import com.elyadata.feature.notification.domain.model.NotificationType;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    void notify(UUID userId, UUID jobId, NotificationType type, String title, String message);

    List<Notification> getUserNotifications(UUID userId);

    long getUnreadCount(UUID userId);

    void markAsRead(UUID notificationId);

    void markAllAsRead(UUID userId);

    void deleteNotification(UUID id, UUID userId);

    void deleteAllNotifications(UUID userId);

    void createAndBroadcast(UUID userId, NotificationType type, String title,
                            String message, String referenceId);
}