package com.elyadata.feature.notification.domain.service.port;

import com.elyadata.feature.notification.domain.model.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationPort {

    Notification save(Notification notification);

    List<Notification> findByUserId(UUID userId);

    long countUnreadByUserId(UUID userId);

    void markAsRead(UUID id);

    void markAllAsReadByUserId(UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);

    void deleteAllByUserId(UUID userId);
}