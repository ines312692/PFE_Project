package com.elyadata.feature.notification.infrastructure.sse;

import com.elyadata.feature.notification.domain.model.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
public class NotificationBroadcaster {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Map<UUID, List<SseConnection>> connections = new ConcurrentHashMap<>();

    public void register(UUID userId, SseEventSink sink, Sse sse) {
        connections.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(new SseConnection(sink, sse));
        log.debug("[SSE] Registered connection for user={}, total={}", userId, connections.get(userId).size());
    }

    public void send(UUID userId, Notification notification) {
        List<SseConnection> sinks = connections.get(userId);
        if (sinks == null || sinks.isEmpty()) return;

        String json;
        try {
            json = MAPPER.writeValueAsString(Map.of(
                    "id", notification.id().toString(),
                    "jobId", notification.jobId() != null ? notification.jobId().toString() : "",
                    "type", notification.type().name(),
                    "title", notification.title(),
                    "message", notification.message() != null ? notification.message() : "",
                    "read", notification.read(),
                    "createdAt", notification.createdAt() != null ? notification.createdAt().toString() : ""
            ));
        } catch (Exception e) {
            log.error("[SSE] Failed to serialize notification", e);
            return;
        }

        List<SseConnection> closed = new java.util.ArrayList<>();

        for (SseConnection conn : sinks) {
            if (conn.sink().isClosed()) {
                closed.add(conn);
                continue;
            }
            try {
                OutboundSseEvent event = conn.sse().newEventBuilder()
                        .name("notification")
                        .data(json)
                        .build();
                conn.sink().send(event);
            } catch (Exception e) {
                log.debug("[SSE] Failed to send to user={}, removing sink", userId);
                closed.add(conn);
            }
        }

        sinks.removeAll(closed);
        if (sinks.isEmpty()) connections.remove(userId);
    }

    private record SseConnection(SseEventSink sink, Sse sse) {
    }
}