package com.elyadata.feature.proxy.infrastructure.adapter;

import com.elyadata.common.pagination.Page;
import com.elyadata.common.utils.PageParam;
import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;
import com.elyadata.feature.proxy.domain.service.port.ProxyPort;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyEntity;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyHealthCheckEntity;
import com.elyadata.feature.proxy.infrastructure.mapper.ProxyAntiBotStatusEntityMapper;
import com.elyadata.feature.proxy.infrastructure.mapper.ProxyEntityMapper;
import com.elyadata.feature.proxy.infrastructure.repository.ProxyAntiBotStatusRepository;
import com.elyadata.feature.proxy.infrastructure.repository.ProxyHealthCheckRepository;
import com.elyadata.feature.proxy.infrastructure.repository.ProxyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class ProxyAdapter implements ProxyPort {

    private static final String PARAM_STATUS = "status";

    private final ProxyRepository proxyRepository;
    private final ProxyHealthCheckRepository healthRepository;
    private final ProxyEntityMapper mapper;
    private final ProxyAntiBotStatusRepository antiBotRepo;
    private final ProxyAntiBotStatusEntityMapper antiBotMapper;

    @PersistenceContext
    EntityManager em;

    public ProxyAdapter(ProxyRepository proxyRepository,
                        ProxyHealthCheckRepository healthRepository,
                        ProxyEntityMapper mapper,
                        ProxyAntiBotStatusRepository antiBotRepo,
                        ProxyAntiBotStatusEntityMapper antiBotMapper) {
        this.proxyRepository = proxyRepository;
        this.healthRepository = healthRepository;
        this.mapper = mapper;
        this.antiBotRepo = antiBotRepo;
        this.antiBotMapper = antiBotMapper;
    }

    @Override
    @Transactional
    public Proxy save(Proxy proxy) {
        ProxyEntity existing = proxy.id() != null
                ? proxyRepository.findById(proxy.id())
                : proxyRepository.findByEndpoint(proxy.host(), proxy.port(), proxy.protocol()).orElse(null);
        if (existing == null) {
            ProxyEntity created = mapper.toEntity(proxy);
            proxyRepository.persist(created);
            return mapper.toDomain(created);
        }
        applyMerge(existing, proxy);
        return mapper.toDomain(existing);
    }

    @Override
    @Transactional
    public int saveAll(List<Proxy> proxies) {
        int inserted = 0;
        for (Proxy p : proxies) {
            if (p.country() != null && p.country().length() > 2) {
                log.warn("[PROXY-ADAPTER] skipping {}:{} with invalid country='{}'",
                        p.host(), p.port(), p.country());
                continue;
            }
            Optional<ProxyEntity> existing = proxyRepository.findByEndpoint(
                    p.host(), p.port(), p.protocol());
            if (existing.isEmpty()) {
                proxyRepository.persist(mapper.toEntity(p));
                inserted++;
            }
        }
        return inserted;
    }

    @Override
    public Optional<Proxy> findById(UUID id) {
        return Optional.ofNullable(proxyRepository.findById(id)).map(mapper::toDomain);
    }

    @Override
    public Page<Proxy> findAll(ProxySearchCriteria criteria, PageParam pageParam) {
        ProxyRepository.SearchResult result =
                proxyRepository.search(criteria, pageParam.getIndex(), pageParam.getSize());
        List<Proxy> content = result.content().stream().map(mapper::toDomain).toList();
        return new Page<>(content, result.total(), pageParam.getIndex(), pageParam.getSize());
    }

    @Override
    public List<Proxy> findActive() {
        return proxyRepository.findActiveOldestCheckedFirst()
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Proxy> findByEndpoint(String host, Integer port, ProxyProtocol protocol) {
        return proxyRepository.findByEndpoint(host, port, protocol).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Proxy> tryAcquire(List<String> countries,
                                      List<ProxyProtocol> protocols,
                                      List<ProxyAnonymity> anonymityLevels,
                                      Integer maxLatencyMs,
                                      Double minSuccessRate,
                                      List<UUID> excludedIds,
                                      String orderByClause,
                                      Instant leasedUntil) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM proxies
                WHERE status = 'ACTIVE'
                  AND (leased_until IS NULL OR leased_until < NOW())
                  AND last_checked_at IS NOT NULL
                  AND latency_ms IS NOT NULL
                """);

        List<Object> params = new ArrayList<>();

        if (countries != null && !countries.isEmpty()) {
            sql.append(" AND country = ANY(?").append(1).append(")");
            params.add(countries.toArray(new String[0]));
        }
        if (protocols != null && !protocols.isEmpty()) {
            sql.append(" AND protocol = ANY(?").append(params.size() + 1).append(")");
            params.add(protocols.stream().map(Enum::name).toArray(String[]::new));
        }
        if (anonymityLevels != null && !anonymityLevels.isEmpty()) {
            sql.append(" AND anonymity = ANY(?").append(params.size() + 1).append(")");
            params.add(anonymityLevels.stream().map(Enum::name).toArray(String[]::new));
        }
        if (maxLatencyMs != null) {
            sql.append(" AND latency_ms <= ?").append(params.size() + 1);
            params.add(maxLatencyMs);
        }
        if (minSuccessRate != null) {
            sql.append(" AND (total_uses = 0 OR success_rate >= ?").append(params.size() + 1).append(")");
            params.add(minSuccessRate);
        }
        if (excludedIds != null && !excludedIds.isEmpty()) {
            sql.append(" AND id <> ALL(?").append(params.size() + 1).append(")");
            params.add(excludedIds.toArray(new UUID[0]));
        }

        sql.append(" ORDER BY ").append(safeOrderBy(orderByClause));
        sql.append(" LIMIT 1 FOR UPDATE SKIP LOCKED");

        Query query = em.createNativeQuery(sql.toString(), ProxyEntity.class);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<ProxyEntity> rows = query.getResultList();
        if (rows.isEmpty()) {
            log.debug("[PROXY-ADAPTER] tryAcquire — no validated proxy matches filters (excluded={})",
                    excludedIds != null ? excludedIds.size() : 0);
            return Optional.empty();
        }

        ProxyEntity picked = rows.getFirst();
        picked.setLeasedUntil(leasedUntil);
        picked.setLastUsedAt(Instant.now());
        return Optional.of(mapper.toDomain(picked));
    }

    @Override
    @Transactional
    public void release(UUID proxyId, boolean success, Integer latencyMs) {
        ProxyEntity p = proxyRepository.findById(proxyId);
        if (p == null) return;

        long total = (p.getTotalUses() == null ? 0 : p.getTotalUses()) + 1;
        long ok = (p.getSuccessfulUses() == null ? 0 : p.getSuccessfulUses()) + (success ? 1 : 0);
        p.setTotalUses(total);
        p.setSuccessfulUses(ok);
        p.setSuccessRate((double) ok / total);
        p.setLastCheckedAt(Instant.now());
        p.setLeasedUntil(null);
        if (latencyMs != null) {
            p.setLatencyMs(latencyMs);
        }
    }

    @Override
    @Transactional
    public void recordUsage(UUID proxyId, UUID jobId, boolean success,
                            Integer latencyMs, String errorMessage) {
        em.createNativeQuery("""
                        INSERT INTO proxy_usage (proxy_id, job_id, success, latency_ms, error_message)
                        VALUES (?1, ?2, ?3, ?4, ?5)
                        """)
                .setParameter(1, proxyId)
                .setParameter(2, jobId)
                .setParameter(3, success)
                .setParameter(4, latencyMs)
                .setParameter(5, errorMessage)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void updateStatus(UUID id, ProxyStatus status) {
        proxyRepository.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        proxyRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void clearExpiredLeases(Instant now) {
        proxyRepository.clearExpiredLeases(now);
    }

    @Override
    @Transactional
    public void saveHealthCheck(ProxyHealthCheck check) {
        ProxyHealthCheckEntity e = mapper.toEntity(check);
        healthRepository.persist(e);
    }

    @Override
    @Transactional
    public int purgeHealthChecksOlderThan(Instant threshold) {
        return healthRepository.deleteOlderThan(threshold);
    }

    @Override
    public long countByStatus(ProxyStatus status) {
        return proxyRepository.countByStatus(status);
    }

    @Override
    public long countBySource(ProxySource source) {
        return proxyRepository.countBySource(source);
    }

    @Override
    public long countByAntiBotStatus(AntiBotProvider provider, ProtectionStatus status) {
        return antiBotRepo.countByProviderAndStatus(provider, status);
    }

    private void applyMerge(ProxyEntity existing, Proxy incoming) {
        if (incoming.country() != null) existing.setCountry(incoming.country());
        if (incoming.anonymity() != null) existing.setAnonymity(incoming.anonymity());
        if (incoming.source() != null) existing.setSource(incoming.source());
    }

    private static final String ALLOWED_ORDER_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_(),. ";
    private static final String FALLBACK_ORDER = "RANDOM()";

    private static String safeOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) return FALLBACK_ORDER;
        for (int i = 0; i < orderBy.length(); i++) {
            if (ALLOWED_ORDER_CHARS.indexOf(orderBy.charAt(i)) < 0) {
                log.warn("[PROXY-ADAPTER] rejected ORDER BY fragment, falling back: {}", orderBy);
                return FALLBACK_ORDER;
            }
        }
        return orderBy;
    }

    @Override
    @Transactional
    public int deleteInactiveOlderThan(Instant threshold) {
        int count = proxyRepository.deleteInactiveOlderThan(threshold);
        if (count > 0) {
            log.info("[PROXY-ADAPTER] deleted {} INACTIVE proxies last checked before {}",
                    count, threshold);
        }
        return count;
    }

    @Override
    @Transactional
    public void upsertAntiBotStatus(ProxyAntiBotStatus s) {
        antiBotRepo.findOne(s.proxyId(), s.provider())
                .ifPresentOrElse(
                        existing -> {
                            existing.setStatus(s.status());
                            existing.setDetected(s.detected());
                            existing.setBlocked(s.blocked());
                            existing.setLastStatusCode(s.lastStatusCode());
                            existing.setLastCheckedAt(s.lastCheckedAt());
                            existing.setLastError(s.lastError());
                        },
                        () -> antiBotRepo.persist(antiBotMapper.toEntity(s))
                );
    }

    @Override
    public List<ProxyAntiBotStatus> findAntiBotStatusesForProxy(UUID proxyId) {
        return antiBotRepo.findByProxyId(proxyId).stream()
                .map(antiBotMapper::toDomain)
                .toList();
    }

    @Override
    public Map<UUID, List<ProxyAntiBotStatus>> findAntiBotStatusesForProxies(List<UUID> proxyIds) {
        if (proxyIds == null || proxyIds.isEmpty()) return Map.of();
        return antiBotRepo.findByProxyIds(proxyIds).stream()
                .map(antiBotMapper::toDomain)
                .collect(Collectors.groupingBy(ProxyAntiBotStatus::proxyId));
    }

    @Override
    public List<Proxy> findActiveNeedingAntiBotCheck(
            AntiBotProvider provider, Instant olderThan, int limit) {
        String jpql = """
                SELECT p FROM ProxyEntity p
                WHERE p.status = :status
                  AND p.id NOT IN (
                    SELECT s.proxyId FROM ProxyAntiBotStatusEntity s
                    WHERE s.provider = :provider
                      AND s.lastCheckedAt > :olderThan
                  )
                ORDER BY p.lastCheckedAt DESC NULLS LAST
                """;
        return em.createQuery(jpql, ProxyEntity.class)
                .setParameter(PARAM_STATUS, ProxyStatus.ACTIVE)
                .setParameter("provider", provider)
                .setParameter("olderThan", olderThan)
                .setMaxResults(limit)
                .getResultList()
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public ProxyDashboard fetchDashboard() {
        Map<ProxyStatus, Long> statusCounts = countByEnumField(PARAM_STATUS, ProxyStatus.class);
        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long active = statusCounts.getOrDefault(ProxyStatus.ACTIVE, 0L);
        long inactive = statusCounts.getOrDefault(ProxyStatus.INACTIVE, 0L);
        long blacklisted = statusCounts.getOrDefault(ProxyStatus.BLACKLISTED, 0L);

        return ProxyDashboard.builder()
                .total(total)
                .active(active)
                .inactive(inactive)
                .blacklisted(blacklisted)
                .avgLatencyMs(fetchAvgLatencyMs())
                .avgSuccessRate(fetchAvgSuccessRate())
                .antiBotTestedCount(countAntiBotTested())
                .countBySource(countByEnumField("source", ProxySource.class))
                .countByProtocol(countByEnumField("protocol", ProxyProtocol.class))
                .countByAnonymity(countByEnumField("anonymity", ProxyAnonymity.class))
                .topCountries(fetchTopCountries())
                .latencyBuckets(fetchLatencyBuckets())
                .antiBotMatrix(fetchAntiBotMatrix())
                .recentHealthChecks(fetchRecentHealthChecks())
                .lastRefreshAt(fetchLastRefreshAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> Map<E, Long> countByEnumField(String field, Class<E> enumType) {
        List<Object[]> rows = em.createQuery(
                "SELECT p." + field + ", COUNT(p) FROM ProxyEntity p GROUP BY p." + field,
                Object[].class
        ).getResultList();

        Map<E, Long> result = new EnumMap<>(enumType);
        for (Object[] row : rows) {
            if (row[0] != null) result.put((E) row[0], (Long) row[1]);
        }
        return result;
    }

    private Map<String, Long> fetchTopCountries() {
        List<Object[]> rows = em.createQuery("""
                        SELECT p.country, COUNT(p)
                        FROM ProxyEntity p
                        WHERE p.country IS NOT NULL
                        GROUP BY p.country
                        ORDER BY COUNT(p) DESC
                        """, Object[].class)
                .setMaxResults(10)
                .getResultList();

        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    private ProxyDashboard.LatencyBuckets fetchLatencyBuckets() {
        Object[] row = (Object[]) em.createQuery("""
                        SELECT
                          SUM(CASE WHEN p.latencyMs IS NULL                                THEN 1 ELSE 0 END),
                          SUM(CASE WHEN p.latencyMs < 500                                  THEN 1 ELSE 0 END),
                          SUM(CASE WHEN p.latencyMs >= 500 AND p.latencyMs < 1500          THEN 1 ELSE 0 END),
                          SUM(CASE WHEN p.latencyMs >= 1500                                THEN 1 ELSE 0 END)
                        FROM ProxyEntity p
                        WHERE p.status = :status
                        """)
                .setParameter(PARAM_STATUS, ProxyStatus.ACTIVE)
                .getSingleResult();

        return ProxyDashboard.LatencyBuckets.builder()
                .unknown(toLong(row[0]))
                .fast(toLong(row[1]))
                .medium(toLong(row[2]))
                .slow(toLong(row[3]))
                .build();
    }

    private Double fetchAvgLatencyMs() {
        Object r = em.createQuery("""
                        SELECT AVG(p.latencyMs) FROM ProxyEntity p
                        WHERE p.status = :status AND p.latencyMs IS NOT NULL
                        """)
                .setParameter(PARAM_STATUS, ProxyStatus.ACTIVE)
                .getSingleResult();
        return r == null ? null : ((Number) r).doubleValue();
    }

    private Double fetchAvgSuccessRate() {
        Object r = em.createQuery("""
                        SELECT AVG(p.successRate) FROM ProxyEntity p
                        WHERE p.status = :status AND p.totalUses > 0
                        """)
                .setParameter(PARAM_STATUS, ProxyStatus.ACTIVE)
                .getSingleResult();
        return r == null ? null : ((Number) r).doubleValue();
    }

    private long countAntiBotTested() {
        Long r = em.createQuery(
                "SELECT COUNT(DISTINCT s.proxyId) FROM ProxyAntiBotStatusEntity s",
                Long.class
        ).getSingleResult();
        return r == null ? 0L : r;
    }

    private Map<AntiBotProvider, Map<ProtectionStatus, Long>> fetchAntiBotMatrix() {
        List<Object[]> rows = em.createQuery("""
                SELECT s.provider, s.status, COUNT(s)
                FROM ProxyAntiBotStatusEntity s
                GROUP BY s.provider, s.status
                """, Object[].class).getResultList();

        Map<AntiBotProvider, Map<ProtectionStatus, Long>> matrix = new EnumMap<>(AntiBotProvider.class);
        for (Object[] row : rows) {
            AntiBotProvider provider = (AntiBotProvider) row[0];
            ProtectionStatus status = (ProtectionStatus) row[1];
            Long count = (Long) row[2];
            matrix.computeIfAbsent(provider, k -> new EnumMap<>(ProtectionStatus.class))
                    .put(status, count);
        }
        return matrix;
    }

    private List<ProxyDashboard.RecentHealthCheck> fetchRecentHealthChecks() {
        List<ProxyHealthCheckEntity> checks = em.createQuery(
                        "SELECT h FROM ProxyHealthCheckEntity h ORDER BY h.checkedAt DESC",
                        ProxyHealthCheckEntity.class)
                .setMaxResults(10)
                .getResultList();

        if (checks.isEmpty()) return List.of();

        List<UUID> proxyIds = checks.stream()
                .map(ProxyHealthCheckEntity::getProxyId)
                .distinct()
                .toList();

        Map<UUID, ProxyEntity> proxyMap = em.createQuery(
                        "SELECT p FROM ProxyEntity p WHERE p.id IN :ids",
                        ProxyEntity.class)
                .setParameter("ids", proxyIds)
                .getResultList()
                .stream()
                .collect(Collectors.toMap(ProxyEntity::getId, p -> p));

        return checks.stream().map(h -> {
            ProxyEntity p = proxyMap.get(h.getProxyId());
            return ProxyDashboard.RecentHealthCheck.builder()
                    .proxyId(h.getProxyId())
                    .host(p != null ? p.getHost() : "(deleted)")
                    .port(p != null ? p.getPort() : null)
                    .success(Boolean.TRUE.equals(h.getSuccess()))
                    .latencyMs(h.getLatencyMs())
                    .httpStatusCode(h.getHttpStatusCode())
                    .errorMessage(h.getErrorMessage())
                    .checkedAt(h.getCheckedAt())
                    .build();
        }).toList();
    }

    private Instant fetchLastRefreshAt() {
        Object r = em.createQuery("""
                        SELECT MAX(p.createdAt) FROM ProxyEntity p
                        WHERE p.source <> :manual
                        """)
                .setParameter("manual", ProxySource.MANUAL)
                .getSingleResult();
        return r == null ? null : (Instant) r;
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    @Override
    public List<ProxyCountryStat> countByCountry() {
        return proxyRepository.countByCountry();
    }
}