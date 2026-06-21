package com.elyadata.feature.proxy.domain.service;

import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.service.port.ProxyPort;
import com.elyadata.feature.proxy.domain.service.strategy.ProxyRotationStrategy;
import com.elyadata.feature.proxy.domain.service.strategy.ProxyRotationStrategyRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProxyPoolServiceImpl")
class ProxyPoolServiceImplTest {

    @Mock
    ProxyPort proxyPort;
    @Mock
    ProxyRotationStrategyRegistry strategyRegistry;
    @Mock
    ProxyRotationStrategy strategy;

    private ProxyPoolServiceImpl service;

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID PROXY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service = new ProxyPoolServiceImpl(proxyPort, strategyRegistry);
        Field f = ProxyPoolServiceImpl.class.getDeclaredField("leaseMinutes");
        f.setAccessible(true);
        f.set(service, 60);

        lenient().when(strategyRegistry.get(any())).thenReturn(strategy);
        lenient().when(strategy.type()).thenReturn(ProxyRotationStrategyType.ROUND_ROBIN);
        lenient().when(strategy.orderByClause()).thenReturn("latency_ms ASC");
    }

    private ProxyConfig enabledConfig() {
        return ProxyConfig.builder()
                .enabled(true)
                .rotationStrategy(ProxyRotationStrategyType.ROUND_ROBIN)
                .build();
    }

    private Proxy mockProxy() {
        return Proxy.builder()
                .id(PROXY_ID)
                .host("10.0.0.1")
                .port(8080)
                .protocol(ProxyProtocol.HTTP)
                .status(ProxyStatus.ACTIVE)
                .successRate(0.9)
                .totalUses(10L)
                .successfulUses(9L)
                .build();
    }


    @Nested
    @DisplayName("acquire")
    class Acquire {

        @Test
        @DisplayName("returns empty when config is null")
        void nullConfigReturnsEmpty() {
            assertThat(service.acquire(null, JOB_ID, List.of())).isEmpty();
            verify(proxyPort, never()).tryAcquire(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("returns empty when config is disabled")
        void disabledConfigReturnsEmpty() {
            ProxyConfig cfg = ProxyConfig.builder().enabled(false).build();
            assertThat(service.acquire(cfg, JOB_ID, List.of())).isEmpty();
        }

        @Test
        @DisplayName("returns proxy when pool has available proxy")
        void returnsProxyWhenAvailable() {
            Proxy proxy = mockProxy();
            when(proxyPort.tryAcquire(any(), any(), any(), any(), any(), any(), any(), any(Instant.class)))
                    .thenReturn(Optional.of(proxy));

            Optional<Proxy> result = service.acquire(enabledConfig(), JOB_ID, List.of());

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(PROXY_ID);
        }

        @Test
        @DisplayName("returns empty when no proxy available and no geo fallback needed")
        void emptyWhenNoProxyAvailable() {
            when(proxyPort.tryAcquire(any(), any(), any(), any(), any(), any(), any(), any(Instant.class)))
                    .thenReturn(Optional.empty());

            Optional<Proxy> result = service.acquire(enabledConfig(), JOB_ID, List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("geo fallback: retries without country filter when strictGeo=false and first attempt fails")
        void geoFallbackWhenNotStrict() {
            ProxyConfig cfg = ProxyConfig.builder()
                    .enabled(true)
                    .rotationStrategy(ProxyRotationStrategyType.ROUND_ROBIN)
                    .countries(List.of("FR"))
                    .strictGeo(false)
                    .build();

            Proxy fallbackProxy = mockProxy();
            // First call (with countries) returns empty, second (without) returns proxy
            when(proxyPort.tryAcquire(any(), any(), any(), any(), any(), any(), any(), any(Instant.class)))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(fallbackProxy));

            Optional<Proxy> result = service.acquire(cfg, JOB_ID, List.of());

            assertThat(result).isPresent();
            verify(proxyPort, times(2)).tryAcquire(any(), any(), any(), any(), any(), any(), any(), any(Instant.class));
        }

        @Test
        @DisplayName("no geo fallback when strictGeo=true")
        void noFallbackWhenStrictGeo() {
            ProxyConfig cfg = ProxyConfig.builder()
                    .enabled(true)
                    .rotationStrategy(ProxyRotationStrategyType.ROUND_ROBIN)
                    .countries(List.of("FR"))
                    .strictGeo(true)
                    .build();

            when(proxyPort.tryAcquire(any(), any(), any(), any(), any(), any(), any(), any(Instant.class)))
                    .thenReturn(Optional.empty());

            Optional<Proxy> result = service.acquire(cfg, JOB_ID, List.of());

            assertThat(result).isEmpty();
            verify(proxyPort, times(1)).tryAcquire(any(), any(), any(), any(), any(), any(), any(), any(Instant.class));
        }

        @Test
        @DisplayName("excluded IDs are passed to the port")
        void excludedIdsPassedToPort() {
            UUID excludedId = UUID.randomUUID();
            when(proxyPort.tryAcquire(any(), any(), any(), any(), any(), eq(List.of(excludedId)), any(), any()))
                    .thenReturn(Optional.empty());

            service.acquire(enabledConfig(), JOB_ID, List.of(excludedId));

            verify(proxyPort).tryAcquire(any(), any(), any(), any(), any(),
                    eq(List.of(excludedId)), any(), any());
        }
    }


    @Nested
    @DisplayName("release")
    class Release {

        @Test
        @DisplayName("calls proxyPort.release and proxyPort.recordUsage")
        void releaseDelegatesToPort() {
            ProxyOutcome outcome = ProxyOutcome.success(120);

            service.release(PROXY_ID, JOB_ID, outcome);

            verify(proxyPort).release(PROXY_ID, true, 120);
            verify(proxyPort).recordUsage(eq(PROXY_ID), eq(JOB_ID), eq(true), eq(120), isNull());
        }

        @Test
        @DisplayName("null proxyId is a no-op")
        void nullProxyIdNoOp() {
            service.release(null, JOB_ID, ProxyOutcome.success(50));
            verify(proxyPort, never()).release(any(), anyBoolean(), anyInt());
        }

        @Test
        @DisplayName("port exception during release is swallowed gracefully")
        void portExceptionSwallowed() {
            doThrow(new RuntimeException("db error")).when(proxyPort)
                    .release(any(), anyBoolean(), anyInt());

            assertThatCode(() -> service.release(PROXY_ID, JOB_ID, ProxyOutcome.success(50)))
                    .doesNotThrowAnyException();
        }
    }
}
