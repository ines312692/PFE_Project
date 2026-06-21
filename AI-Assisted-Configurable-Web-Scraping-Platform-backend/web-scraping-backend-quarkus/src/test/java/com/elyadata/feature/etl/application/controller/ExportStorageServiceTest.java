package com.elyadata.feature.etl.application.controller;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportStorageService")
class ExportStorageServiceTest {

    private ExportStorageService service;

    @BeforeEach
    void setUp() {
        service = new ExportStorageService();
    }

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("returns a non-null UUID-like export ID")
        void returnsNonNullId() {
            String id = service.store("data".getBytes(), "file.csv", "text/csv");
            assertThat(id).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("each store call returns a unique ID")
        void uniqueIds() {
            String id1 = service.store("a".getBytes(), "a.csv", "text/csv");
            String id2 = service.store("b".getBytes(), "b.csv", "text/csv");
            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("stored entry is retrievable immediately")
        void retrievableAfterStore() {
            byte[] content = "hello".getBytes();
            String id = service.store(content, "export.json", "application/json");

            Optional<ExportStorageService.ExportEntry> entry = service.get(id);

            assertThat(entry).isPresent();
            assertThat(entry.get().filename()).isEqualTo("export.json");
            assertThat(entry.get().contentType()).isEqualTo("application/json");
            assertThat(entry.get().content()).isEqualTo(content);
        }
    }


    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("returns empty when ID does not exist")
        void emptyForUnknownId() {
            assertThat(service.get("non-existent-id")).isEmpty();
        }

        @Test
        @DisplayName("returns empty and removes entry when export is expired")
        void emptyAndRemovedWhenExpired() throws Exception {
            byte[] content = "expired".getBytes();
            String id = service.store(content, "old.csv", "text/csv");
            backdateEntry(id, Instant.now().minus(16, ChronoUnit.MINUTES));

            Optional<ExportStorageService.ExportEntry> result = service.get(id);

            assertThat(result).isEmpty();
            assertThat(service.get(id)).isEmpty();
        }

        @Test
        @DisplayName("returns entry when just within expiry window")
        void presentWithinWindow() throws Exception {
            String id = service.store("fresh".getBytes(), "fresh.csv", "text/csv");
            // Backdate to 14 minutes ago — still within 15-min TTL
            backdateEntry(id, Instant.now().minus(14, ChronoUnit.MINUTES));

            assertThat(service.get(id)).isPresent();
        }
    }


    @Nested
    @DisplayName("cleanup")
    class Cleanup {

        @Test
        @DisplayName("cleanup removes expired entries and keeps fresh ones")
        void removesExpiredKeepsFresh() throws Exception {
            String expiredId = service.store("old".getBytes(), "old.csv", "text/csv");
            String freshId   = service.store("new".getBytes(), "new.csv", "text/csv");

            backdateEntry(expiredId, Instant.now().minus(20, ChronoUnit.MINUTES));

            service.cleanup();

            assertThat(service.get(expiredId)).isEmpty();
            assertThat(service.get(freshId)).isPresent();
        }

        @Test
        @DisplayName("cleanup on empty store does not throw")
        void cleanupOnEmptyStore() {
            assertThatCode(() -> service.cleanup()).doesNotThrowAnyException();
        }
    }


    @Nested
    @DisplayName("ExportEntry record")
    class ExportEntryRecord {

        @Test
        @DisplayName("two entries with same fields are equal")
        void equalWhenSameFields() {
            byte[] data = "x".getBytes();
            Instant now  = Instant.now();
            var e1 = new ExportStorageService.ExportEntry(data, "file.csv", "text/csv", now);
            var e2 = new ExportStorageService.ExportEntry(data, "file.csv", "text/csv", now);
            assertThat(e1).isEqualTo(e2);
            assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        }

        @Test
        @DisplayName("toString does not throw")
        void toStringDoesNotThrow() {
            var entry = new ExportStorageService.ExportEntry(
                    "data".getBytes(), "f.csv", "text/csv", Instant.now());
            assertThatCode(entry::toString).doesNotThrowAnyException();
        }
    }


    @SuppressWarnings("unchecked")
    private void backdateEntry(String id, Instant newCreatedAt) throws Exception {
        Field storeField = ExportStorageService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        Map<String, ExportStorageService.ExportEntry> storeMap =
                (Map<String, ExportStorageService.ExportEntry>) storeField.get(service);

        storeMap.computeIfPresent(id, (k, existing) -> new ExportStorageService.ExportEntry(
                existing.content(), existing.filename(), existing.contentType(), newCreatedAt));
    }
}
