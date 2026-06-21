package com.elyadata.feature.scraper.browser.session;

import com.elyadata.common.config.SessionCrypto;
import com.elyadata.feature.scraper.browser.session.entity.BrowserSessionEntity;
import com.elyadata.feature.scraper.browser.session.repository.BrowserSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TieredSessionStore implements SessionStorePort {

    private final BrowserSessionRepository repository;
    private final SessionCrypto crypto;

    private final ConcurrentMap<String, SessionState> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<SessionState> find(String domain) {
        String key = norm(domain);

        SessionState cached = cache.get(key);
        if (cached != null) {
            if (cached.isUsable()) {
                log.info("[SESSION] cache HIT domain={}", key);
                return Optional.of(cached);
            }
            cache.remove(key);
            log.debug("[SESSION] cache stale domain={} — dropping", key);
        }

        return loadFromDb(key);
    }

    @Transactional
    protected Optional<SessionState> loadFromDb(String key) {
        return repository.findByDomain(key).flatMap(entity -> {
            SessionState state = toState(entity);
            if (state.isEmpty()) {
                return Optional.empty();
            }
            cache.put(key, state);
            log.info("[SESSION] DB HIT domain={} (cache warmed)", key);
            return Optional.of(state);
        });
    }

    @Override
    @Transactional
    public void save(SessionState state) {
        if (state == null || state.isEmpty()) {
            log.debug("[SESSION] save skipped — empty state");
            return;
        }
        String key = norm(state.domain());
        SessionState normalized = state.toBuilder().domain(key).build();

        cache.put(key, normalized);

        String enc = crypto.encrypt(normalized.storageState());
        Optional<BrowserSessionEntity> existing = repository.findByDomain(key);

        if (existing.isPresent()) {
            // Managed within this @Transactional unit — field updates are flushed on commit.
            BrowserSessionEntity entity = existing.get();
            entity.setStorageStateEnc(enc);
            entity.setCapturedAt(normalized.capturedAt());
            entity.setExpiresAt(normalized.expiresAt());
        } else {
            BrowserSessionEntity entity = BrowserSessionEntity.builder()
                    .domain(key)
                    .storageStateEnc(enc)
                    .capturedAt(normalized.capturedAt())
                    .expiresAt(normalized.expiresAt())
                    .build();
            repository.persist(entity);
        }

        log.info("[SESSION] saved domain={} capturedAt={} expiresAt={}",
                key, normalized.capturedAt(), normalized.expiresAt());
    }

    @Override
    @Transactional
    public void invalidate(String domain) {
        String key = norm(domain);
        cache.remove(key);
        repository.deleteByDomain(key);
        log.info("[SESSION] invalidated domain={}", key);
    }

    private SessionState toState(BrowserSessionEntity e) {
        try {
            return SessionState.builder()
                    .domain(e.getDomain())
                    .storageState(crypto.decrypt(e.getStorageStateEnc()))
                    .capturedAt(e.getCapturedAt())
                    .expiresAt(e.getExpiresAt())
                    .build();
        } catch (Exception ex) {
            log.warn("[SESSION] decrypt failed domain={} — ignoring stored session: {}",
                    e.getDomain(), ex.getMessage());
            return SessionState.builder().domain(e.getDomain()).build();
        }
    }

    private static String norm(String domain) {
        return domain == null ? "" : domain.toLowerCase().strip();
    }
}