package com.elyadata.feature.proxy.infrastructure.adapter;

import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyConfigEntity;
import com.elyadata.feature.proxy.infrastructure.mapper.ProxyEntityMapper;
import com.elyadata.feature.proxy.infrastructure.repository.ProxyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProxyConfigAdapter")
class ProxyConfigAdapterTest {

    @Mock
    ProxyConfigRepository repository;

    @Mock
    ProxyEntityMapper mapper;

    ProxyConfigAdapter adapter;

    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        adapter = new ProxyConfigAdapter(repository, mapper);
    }

    @Nested
    @DisplayName("saveOrUpdate()")
    class SaveOrUpdate {

        @Test
        @DisplayName("uses ProxyConfig.disabled() when config param is null")
        void nullConfig_usesDisabled() {
            ProxyConfigEntity entity = new ProxyConfigEntity();
            ProxyConfig disabledConfig = ProxyConfig.disabled();
            when(repository.findByJobId(JOB_ID)).thenReturn(Optional.empty());
            when(mapper.toEntity(any(ProxyConfig.class), eq(JOB_ID))).thenReturn(entity);
            when(mapper.toDomain(entity)).thenReturn(disabledConfig);
            doNothing().when(repository).persist(entity);

            ProxyConfig result = adapter.saveOrUpdate(JOB_ID, null);

            assertThat(result).isNotNull();
            verify(mapper).toEntity(any(ProxyConfig.class), eq(JOB_ID));
            verify(repository).persist(entity);
        }

        @Test
        @DisplayName("persists new entity when no existing entity found")
        void noExistingEntity_persistsNew() {
            ProxyConfig config = ProxyConfig.disabled();
            ProxyConfigEntity entity = new ProxyConfigEntity();
            when(repository.findByJobId(JOB_ID)).thenReturn(Optional.empty());
            when(mapper.toEntity(config, JOB_ID)).thenReturn(entity);
            when(mapper.toDomain(entity)).thenReturn(config);
            doNothing().when(repository).persist(entity);

            ProxyConfig result = adapter.saveOrUpdate(JOB_ID, config);

            assertThat(result).isNotNull();
            verify(repository).persist(entity);
            verify(repository, never()).flush();
        }

        @Test
        @DisplayName("updates existing entity and flushes when entity already exists")
        void existingEntity_updatesAndFlushes() {
            ProxyConfig config = ProxyConfig.disabled();
            ProxyConfigEntity existingEntity = new ProxyConfigEntity();
            ProxyConfigEntity mappedEntity = new ProxyConfigEntity();
            when(repository.findByJobId(JOB_ID)).thenReturn(Optional.of(existingEntity));
            when(mapper.toEntity(config, JOB_ID)).thenReturn(mappedEntity);
            when(mapper.toDomain(existingEntity)).thenReturn(config);
            doNothing().when(repository).flush();

            ProxyConfig result = adapter.saveOrUpdate(JOB_ID, config);

            assertThat(result).isNotNull();
            verify(repository, never()).persist(any());
            verify(repository).flush();
        }
    }

    @Nested
    @DisplayName("findByJobId()")
    class FindByJobId {

        @Test
        @DisplayName("returns mapped domain when entity found")
        void entityFound_returnsMapped() {
            ProxyConfigEntity entity = new ProxyConfigEntity();
            ProxyConfig config = ProxyConfig.disabled();
            when(repository.findByJobId(JOB_ID)).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(config);

            Optional<ProxyConfig> result = adapter.findByJobId(JOB_ID);

            assertThat(result).contains(config);
        }

        @Test
        @DisplayName("returns empty Optional when entity not found")
        void entityNotFound_returnsEmpty() {
            when(repository.findByJobId(JOB_ID)).thenReturn(Optional.empty());

            Optional<ProxyConfig> result = adapter.findByJobId(JOB_ID);

            assertThat(result).isEmpty();
            verify(mapper, never()).toDomain(any(ProxyConfigEntity.class));
        }
    }

    @Nested
    @DisplayName("deleteByJobId()")
    class DeleteByJobId {

        @Test
        @DisplayName("delegates deletion to repository")
        void delegatesToRepository() {
            adapter.deleteByJobId(JOB_ID);

            verify(repository).deleteByJobId(JOB_ID);
        }
    }
}
