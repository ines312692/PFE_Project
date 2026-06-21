package com.elyadata.feature.etl.application.controller;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@ApplicationScoped
public class ExportStorageService {

    private static final int EXPIRY_MINUTES = 15;

    private final Map<String, ExportEntry> store = new ConcurrentHashMap<>();

    public String store(byte[] content, String filename, String contentType) {
        String exportId = UUID.randomUUID().toString();
        store.put(exportId, new ExportEntry(content, filename, contentType, Instant.now()));
        log.info("[EXPORT-STORAGE] Stored export id={} file={} size={}b", exportId, filename, content.length);
        return exportId;
    }

    public Optional<ExportEntry> get(String exportId) {
        ExportEntry entry = store.get(exportId);
        if (entry == null) {
            log.warn("[EXPORT-STORAGE] Export not found: {}", exportId);
            return Optional.empty();
        }
        if (isExpired(entry)) {
            store.remove(exportId);
            log.warn("[EXPORT-STORAGE] Export expired: {}", exportId);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Scheduled(every = "5m")
    void cleanup() {
        int before = store.size();
        store.entrySet().removeIf(e -> isExpired(e.getValue()));
        int removed = before - store.size();
        if (removed > 0) {
            log.info("[EXPORT-STORAGE] Cleaned up {} expired export(s)", removed);
        }
    }

    private boolean isExpired(ExportEntry entry) {
        return entry.createdAt().isBefore(Instant.now().minus(EXPIRY_MINUTES, ChronoUnit.MINUTES));
    }

    public record ExportEntry(byte[] content, String filename, String contentType, Instant createdAt) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExportEntry(byte[] content1, String filename1, String type, Instant at))) return false;
            return java.util.Arrays.equals(this.content, content1) &&
                    java.util.Objects.equals(this.filename, filename1) &&
                    java.util.Objects.equals(this.contentType, type) &&
                    java.util.Objects.equals(this.createdAt, at);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(java.util.Arrays.hashCode(content), filename, contentType, createdAt);
        }

        @Override
        public @NonNull String toString() {
            return "ExportEntry{" +
                    "content=" + java.util.Arrays.toString(content) +
                    ", filename='" + filename + '\'' +
                    ", contentType='" + contentType + '\'' +
                    ", createdAt=" + createdAt +
                    '}';
        }
    }

}