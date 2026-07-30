package com.elyadata.feature.audit.domain.service;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.domain.model.AuditLogSearchCriteria;
import com.elyadata.feature.audit.domain.model.AuditLogStats;
import com.elyadata.feature.audit.domain.service.port.AuditLogPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogPort auditLogPort;

    private static final ExecutorService AUDIT_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "audit-writer");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void record(AuditLog auditLog) {
        CompletableFuture.runAsync(() -> {
            try {
                doRecord(auditLog);
            } catch (Exception e) {
                log.warn("[AUDIT] Failed to persist audit log: {}", e.getMessage());
            }
        }, AUDIT_EXECUTOR);
    }

    @Transactional
    void doRecord(AuditLog auditLog) {
        auditLogPort.save(auditLog);
        log.debug("[AUDIT] Recorded {} {} on {} by user={}",
                auditLog.action(), auditLog.outcome(),
                auditLog.resourceType(), auditLog.username());
    }

    @Override
    public Page<AuditLog> search(AuditLogSearchCriteria criteria, int page, int size) {
        return auditLogPort.search(criteria, page, size);
    }

    @Override
    public AuditLog getById(UUID id) {
        return auditLogPort.findById(id);
    }

    @Override
    public AuditLogStats getStats() {
        return auditLogPort.getStats();
    }

    @Override
    public void delete(UUID id) {
        auditLogPort.findById(id);
        auditLogPort.delete(id);
        log.info("[AUDIT] Manually deleted audit log: {}", id);
    }
}