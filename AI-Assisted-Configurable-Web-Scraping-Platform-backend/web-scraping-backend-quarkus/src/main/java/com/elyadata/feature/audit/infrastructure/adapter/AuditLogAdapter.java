package com.elyadata.feature.audit.infrastructure.adapter;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.domain.model.AuditLogSearchCriteria;
import com.elyadata.feature.audit.domain.model.AuditLogStats;
import com.elyadata.feature.audit.domain.model.AuditOutcome;
import com.elyadata.feature.audit.domain.service.port.AuditLogPort;
import com.elyadata.feature.audit.infrastructure.entity.AuditLogEntity;
import com.elyadata.feature.audit.infrastructure.mapper.AuditLogEntityMapper;
import com.elyadata.feature.audit.infrastructure.repository.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class AuditLogAdapter implements AuditLogPort {

    private static final String AUDIT_PATH = "/v1/audit-logs";

    private final AuditLogRepository repository;
    private final AuditLogEntityMapper mapper;

    @Override
    @Transactional
    public AuditLog save(AuditLog auditLog) {
        AuditLogEntity entity = mapper.toEntity(auditLog);
        repository.persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Page<AuditLog> search(AuditLogSearchCriteria criteria, int page, int size) {
        long total = repository.countByCriteria(criteria);
        List<AuditLogEntity> rows = repository.searchByCriteria(criteria, page, size);

        List<AuditLog> content = rows.stream()
                .map(mapper::toDomain)
                .toList();

        return new Page<>(content, total, page, size);
    }

    @Override
    public AuditLog findById(UUID id) {
        AuditLogEntity entity = repository.findById(id);
        if (entity == null) {
            throw DomainException.notFound("Audit log not found: " + id, AUDIT_PATH + "/" + id);
        }
        return mapper.toDomain(entity);
    }

    @Override
    public AuditLogStats getStats() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant last24h = Instant.now().minusSeconds(24L * 3600);

        long total = repository.countAll();
        long today = repository.countSince(startOfDay);
        long failuresLast24h = repository.countByOutcomeSince(AuditOutcome.FAILURE, last24h);
        long uniqueUsersLast24h = repository.countDistinctUsersSince(last24h);

        return AuditLogStats.builder()
                .total(total)
                .today(today)
                .failuresLast24h(failuresLast24h)
                .uniqueUsersLast24h(uniqueUsersLast24h)
                .build();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}