package com.elyadata.feature.notification.infrastructure.adapter;

import com.elyadata.feature.notification.domain.model.Notification;
import com.elyadata.feature.notification.domain.service.port.NotificationPort;
import com.elyadata.feature.notification.infrastructure.mapper.NotificationEntityMapper;
import com.elyadata.feature.notification.infrastructure.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class NotificationAdapter implements NotificationPort {

    private final NotificationRepository repository;
    private final NotificationEntityMapper mapper;

    @Override
    @Transactional
    public Notification save(Notification notification) {
        var entity = mapper.toEntity(notification);
        repository.persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public List<Notification> findByUserId(UUID userId) {
        return repository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countUnreadByUserId(UUID userId) {
        return repository.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID id) {
        repository.markAsRead(id);
    }

    @Override
    @Transactional
    public void markAllAsReadByUserId(UUID userId) {
        repository.markAllAsReadByUserId(userId);
    }


    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repository.deleteByIdAndUserId(id, userId);
    }

    @Override
    @Transactional
    public void deleteAllByUserId(UUID userId) {
        repository.deleteAllByUserId(userId);
    }

}