package com.elyadata.feature.audit.domain.service;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.domain.model.AuditLogSearchCriteria;
import com.elyadata.feature.audit.domain.model.AuditLogStats;

import java.util.UUID;

public interface AuditLogService {

    void record(AuditLog auditLog);

    Page<AuditLog> search(AuditLogSearchCriteria criteria, int page, int size);

    AuditLog getById(UUID id);

    AuditLogStats getStats();

    void delete(UUID id);
}