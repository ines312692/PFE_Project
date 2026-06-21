package com.elyadata.feature.notification.infrastructure.mapper;

import com.elyadata.feature.notification.domain.model.Notification;
import com.elyadata.feature.notification.infrastructure.entity.NotificationEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationEntityMapper {

    public Notification toDomain(NotificationEntity entity) {
        return Notification.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .jobId(entity.getJobId())
                .type(entity.getType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .read(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .referenceId(entity.getReferenceId())
                .build();
    }

    public NotificationEntity toEntity(Notification domain) {
        return NotificationEntity.builder()
                .userId(domain.userId())
                .jobId(domain.jobId())
                .type(domain.type())
                .title(domain.title())
                .message(domain.message())
                .read(domain.read())
                .referenceId(domain.referenceId())
                .build();
    }
}