package com.elyadata.feature.audit.domain.service;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.audit.domain.model.*;
import com.elyadata.feature.audit.domain.service.port.AuditLogPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogServiceImpl")
class AuditLogServiceImplTest {

    @Mock AuditLogPort auditLogPort;

    private AuditLogServiceImpl service;

    private static final UUID LOG_ID  = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuditLogServiceImpl(auditLogPort);
    }

    private AuditLog sample() {
        return AuditLog.builder()
                .id(LOG_ID)
                .userId(USER_ID)
                .username("alice")
                .action(AuditAction.CREATE)
                .resourceType(AuditResource.JOB)
                .outcome(AuditOutcome.SUCCESS)
                .occurredAt(Instant.now())
                .build();
    }

    // ─── doRecord() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("doRecord()")
    class DoRecord {

        @Test
        @DisplayName("calls auditLogPort.save with the provided log")
        void callsPortSave() {
            AuditLog log = sample();
            service.doRecord(log);
            verify(auditLogPort).save(log);
        }

        @Test
        @DisplayName("does not swallow port exceptions — they propagate to the async wrapper")
        void portExceptionPropagates() {
            doThrow(new RuntimeException("db error")).when(auditLogPort).save(any());
            assertThatThrownBy(() -> service.doRecord(sample()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db error");
        }
    }

    // ─── record() — async ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("record() — async")
    class RecordAsync {

        @Test
        @DisplayName("eventually persists the audit log asynchronously")
        void persistsAsynchronously() throws InterruptedException {
            AuditLog log = sample();
            service.record(log);
            Thread.sleep(300);
            verify(auditLogPort).save(log);
        }

        @Test
        @DisplayName("record() does not throw even when port fails")
        void portExceptionSwallowed() throws InterruptedException {
            doThrow(new RuntimeException("db down")).when(auditLogPort).save(any());
            assertThatCode(() -> service.record(sample())).doesNotThrowAnyException();
            Thread.sleep(300); // wait for async task to invoke save() so stub is not flagged unused
        }
    }

    // ─── search() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("delegates to port with criteria and pagination params")
        void delegatesToPort() {
            AuditLogSearchCriteria criteria = AuditLogSearchCriteria.builder()
                    .search("alice")
                    .outcome(AuditOutcome.SUCCESS)
                    .build();
            Page<AuditLog> page = new Page<>(List.of(sample()), 1L, 0, 20);
            when(auditLogPort.search(criteria, 0, 20)).thenReturn(page);

            assertThat(service.search(criteria, 0, 20)).isEqualTo(page);
        }

        @Test
        @DisplayName("returns empty page when no results")
        void returnsEmptyPage() {
            AuditLogSearchCriteria criteria = AuditLogSearchCriteria.builder().build();
            Page<AuditLog> empty = new Page<>(List.of(), 0L, 0, 10);
            when(auditLogPort.search(criteria, 0, 10)).thenReturn(empty);

            assertThat(service.search(criteria, 0, 10).content()).isEmpty();
        }
    }

    // ─── getById() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("returns audit log from port")
        void returnsFromPort() {
            AuditLog log = sample();
            when(auditLogPort.findById(LOG_ID)).thenReturn(log);
            assertThat(service.getById(LOG_ID)).isEqualTo(log);
        }
    }

    // ─── getStats() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("returns stats from port")
        void returnsStatsFromPort() {
            AuditLogStats stats = AuditLogStats.builder()
                    .total(500).today(30).failuresLast24h(3).uniqueUsersLast24h(12).build();
            when(auditLogPort.getStats()).thenReturn(stats);

            AuditLogStats result = service.getStats();
            assertThat(result.total()).isEqualTo(500);
            assertThat(result.today()).isEqualTo(30);
            assertThat(result.failuresLast24h()).isEqualTo(3);
            assertThat(result.uniqueUsersLast24h()).isEqualTo(12);
        }
    }

    // ─── delete() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("calls findById then delete on the port")
        void callsFindByIdThenDelete() {
            when(auditLogPort.findById(LOG_ID)).thenReturn(sample());
            service.delete(LOG_ID);
            InOrder inOrder = inOrder(auditLogPort);
            inOrder.verify(auditLogPort).findById(LOG_ID);
            inOrder.verify(auditLogPort).delete(LOG_ID);
        }

        @Test
        @DisplayName("propagates exception from findById without calling delete")
        void propagatesExceptionFromFindById() {
            doThrow(new RuntimeException("not found")).when(auditLogPort).findById(any());
            assertThatThrownBy(() -> service.delete(LOG_ID))
                    .isInstanceOf(RuntimeException.class);
            verify(auditLogPort, never()).delete(any());
        }
    }
}