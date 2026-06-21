package com.elyadata.feature.proxy.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.common.utils.PageParam;
import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.model.antibot.*;
import com.elyadata.feature.proxy.domain.service.antibot.ProxyAntiBotDetectorRegistry;
import com.elyadata.feature.proxy.domain.service.port.ProxyPort;
import com.elyadata.feature.proxy.domain.service.source.ProxySourceRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProxyServiceImpl")
class ProxyServiceImplTest {

    @Mock ProxyPort proxyPort;
    @Mock ProxySourceRegistry sources;
    @Mock ProxyHealthChecker healthChecker;
    @Mock ProxyAntiBotDetectorRegistry detectorRegistry;

    private ProxyServiceImpl service;

    private static final UUID PROXY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProxyServiceImpl(proxyPort, sources, healthChecker, detectorRegistry);
    }

    private Proxy activeProxy() {
        return Proxy.builder()
                .id(PROXY_ID)
                .host("10.0.0.1")
                .port(8080)
                .protocol(ProxyProtocol.HTTP)
                .status(ProxyStatus.ACTIVE)
                .anonymity(ProxyAnonymity.ANONYMOUS)
                .source(ProxySource.MANUAL)
                .successRate(0.9)
                .totalUses(10L)
                .successfulUses(9L)
                .build();
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns proxy when found")
        void returnsProxy() {
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            assertThat(service.findById(PROXY_ID)).isEqualTo(activeProxy());
        }

        @Test
        @DisplayName("throws DomainException when not found")
        void throwsWhenNotFound() {
            when(proxyPort.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(PROXY_ID))
                    .isInstanceOf(DomainException.class);
        }
    }


    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("delegates to port and returns paged result")
        void delegatesToPort() {
            ProxySearchCriteria criteria = ProxySearchCriteria.builder().build();
            PageParam pageParam = PageParam.builder().index(0).size(10).build();
            Page<Proxy> page = new Page<>(List.of(activeProxy()), 1L, 0, 10);
            when(proxyPort.findAll(criteria, pageParam)).thenReturn(page);

            assertThat(service.findAll(criteria, pageParam)).isEqualTo(page);
        }
    }

    @Nested
    @DisplayName("getAntiBotStatuses()")
    class GetAntiBotStatuses {

        @Test
        @DisplayName("returns statuses when proxy exists")
        void returnsStatuses() {
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            when(proxyPort.findAntiBotStatusesForProxy(PROXY_ID)).thenReturn(List.of());

            assertThat(service.getAntiBotStatuses(PROXY_ID)).isEmpty();
        }

        @Test
        @DisplayName("throws when proxy not found")
        void throwsWhenNotFound() {
            when(proxyPort.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getAntiBotStatuses(PROXY_ID))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("refreshPool()")
    class RefreshPool {

        @Test
        @DisplayName("skips fetch when pool is at or over capacity (1500)")
        void skipsWhenAtCapacity() {
            when(proxyPort.countByStatus(ProxyStatus.ACTIVE)).thenReturn(1000L);
            when(proxyPort.countByStatus(ProxyStatus.INACTIVE)).thenReturn(400L);
            when(proxyPort.countByStatus(ProxyStatus.BLACKLISTED)).thenReturn(200L);

            assertThat(service.refreshPool()).isZero();
            verify(sources, never()).fetchAll();
        }

        @Test
        @DisplayName("returns 0 when sources return empty list")
        void returns0WhenSourcesEmpty() {
            when(proxyPort.countByStatus(any())).thenReturn(0L);
            when(sources.fetchAll()).thenReturn(List.of());

            assertThat(service.refreshPool()).isZero();
            verify(proxyPort, never()).saveAll(any());
        }

        @Test
        @DisplayName("saves fetched proxies when below capacity")
        void savesFetchedProxies() {
            when(proxyPort.countByStatus(any())).thenReturn(0L);
            List<Proxy> fetched = List.of(activeProxy(), activeProxy());
            when(sources.fetchAll()).thenReturn(fetched);
            when(proxyPort.saveAll(fetched)).thenReturn(2);

            assertThat(service.refreshPool()).isEqualTo(2);
        }
    }


    @Nested
    @DisplayName("createManual()")
    class CreateManual {

        @Test
        @DisplayName("creates proxy successfully when no duplicate exists")
        void createsSuccessfully() {
            when(proxyPort.findByEndpoint("host", 8080, ProxyProtocol.HTTP))
                    .thenReturn(Optional.empty());
            when(proxyPort.save(any())).thenReturn(activeProxy());

            Proxy result = service.createManual("host", 8080, ProxyProtocol.HTTP,
                    null, null, "FR", ProxyAnonymity.ANONYMOUS);

            assertThat(result).isEqualTo(activeProxy());
            verify(proxyPort).save(any(Proxy.class));
        }

        @Test
        @DisplayName("throws DomainException when duplicate endpoint exists")
        void throwsOnDuplicate() {
            when(proxyPort.findByEndpoint("host", 8080, ProxyProtocol.HTTP))
                    .thenReturn(Optional.of(activeProxy()));

            assertThatThrownBy(() -> service.createManual("host", 8080, ProxyProtocol.HTTP,
                    null, null, null, null))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("normalizes country code to uppercase")
        void normalizesCountryToUpperCase() {
            when(proxyPort.findByEndpoint(any(), any(), any())).thenReturn(Optional.empty());
            ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
            when(proxyPort.save(captor.capture())).thenReturn(activeProxy());

            service.createManual("h", 80, ProxyProtocol.HTTP, null, null, "fr", null);

            assertThat(captor.getValue().country()).isEqualTo("FR");
        }

        @Test
        @DisplayName("defaults anonymity to UNKNOWN when null")
        void defaultsAnonymityToUnknown() {
            when(proxyPort.findByEndpoint(any(), any(), any())).thenReturn(Optional.empty());
            ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
            when(proxyPort.save(captor.capture())).thenReturn(activeProxy());

            service.createManual("h", 80, ProxyProtocol.HTTP, null, null, null, null);

            assertThat(captor.getValue().anonymity()).isEqualTo(ProxyAnonymity.UNKNOWN);
        }

        @Test
        @DisplayName("new proxy is created with MANUAL source and ACTIVE status")
        void setsManualSourceAndActiveStatus() {
            when(proxyPort.findByEndpoint(any(), any(), any())).thenReturn(Optional.empty());
            ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
            when(proxyPort.save(captor.capture())).thenReturn(activeProxy());

            service.createManual("h", 80, ProxyProtocol.SOCKS5, null, null, null, null);

            assertThat(captor.getValue().source()).isEqualTo(ProxySource.MANUAL);
            assertThat(captor.getValue().status()).isEqualTo(ProxyStatus.ACTIVE);
        }
    }


    @Nested
    @DisplayName("status operations")
    class StatusOperations {

        @Test
        @DisplayName("blacklist() updates status to BLACKLISTED")
        void blacklist_updatesStatus() {
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            service.blacklist(PROXY_ID);
            verify(proxyPort).updateStatus(PROXY_ID, ProxyStatus.BLACKLISTED);
        }

        @Test
        @DisplayName("reactivate() updates status to ACTIVE")
        void reactivate_updatesStatus() {
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            service.reactivate(PROXY_ID);
            verify(proxyPort).updateStatus(PROXY_ID, ProxyStatus.ACTIVE);
        }

        @Test
        @DisplayName("delete() calls port.delete after finding proxy")
        void delete_callsPortDelete() {
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            service.delete(PROXY_ID);
            verify(proxyPort).delete(PROXY_ID);
        }

        @Test
        @DisplayName("delete() throws DomainException when proxy not found")
        void delete_throwsWhenNotFound() {
            when(proxyPort.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.delete(PROXY_ID))
                    .isInstanceOf(DomainException.class);
        }
    }


    @Nested
    @DisplayName("health checks")
    class HealthChecks {

        private final ProxyHealthCheck successCheck = ProxyHealthCheck.builder()
                .proxyId(PROXY_ID).success(true).latencyMs(50).build();

        @Test
        @DisplayName("triggerHealthCheck() delegates to healthChecker.check")
        void triggerHealthCheck_delegates() {
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            when(healthChecker.check(any(Proxy.class))).thenReturn(successCheck);

            assertThat(service.triggerHealthCheck(PROXY_ID)).isEqualTo(successCheck);
        }

        @Test
        @DisplayName("triggerFullAntiBotCheck() delegates to healthChecker.checkFull")
        void triggerFullCheck_delegates() {
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            when(healthChecker.checkFull(any(Proxy.class))).thenReturn(successCheck);

            assertThat(service.triggerFullAntiBotCheck(PROXY_ID)).isEqualTo(successCheck);
        }

        @Test
        @DisplayName("triggerSingleAntiBotCheck() delegates to healthChecker.checkAntiBot")
        void triggerSingleCheck_delegates() {
            DetectionResult dr = new DetectionResult(
                    AntiBotProvider.CLOUDFLARE, ProtectionStatus.CLEAN,
                    null, false, false, null, Instant.now(), null);
            when(proxyPort.findById(PROXY_ID)).thenReturn(Optional.of(activeProxy()));
            when(healthChecker.checkAntiBot(any(Proxy.class), eq(AntiBotProvider.CLOUDFLARE)))
                    .thenReturn(dr);

            assertThat(service.triggerSingleAntiBotCheck(PROXY_ID, AntiBotProvider.CLOUDFLARE))
                    .isEqualTo(dr);
        }
    }


    @Nested
    @DisplayName("stats()")
    class Stats {

        @Test
        @DisplayName("aggregates counts from port and builds PoolStats")
        void aggregatesCounts() {
            when(proxyPort.countByStatus(ProxyStatus.ACTIVE)).thenReturn(100L);
            when(proxyPort.countByStatus(ProxyStatus.INACTIVE)).thenReturn(20L);
            when(proxyPort.countByStatus(ProxyStatus.BLACKLISTED)).thenReturn(5L);
            when(proxyPort.countBySource(ProxySource.MANUAL)).thenReturn(10L);
            when(detectorRegistry.registeredProviders()).thenReturn(Set.of());

            ProxyService.PoolStats stats = service.stats();

            assertThat(stats.active()).isEqualTo(100L);
            assertThat(stats.inactive()).isEqualTo(20L);
            assertThat(stats.blacklisted()).isEqualTo(5L);
            assertThat(stats.manual()).isEqualTo(10L);
        }

        @Test
        @DisplayName("cloudflareClean is 0 when CLOUDFLARE not in registered providers")
        void cloudflareClean_zeroWhenNotRegistered() {
            when(proxyPort.countByStatus(any())).thenReturn(0L);
            when(proxyPort.countBySource(any())).thenReturn(0L);
            when(detectorRegistry.registeredProviders()).thenReturn(Set.of());

            assertThat(service.stats().cloudflareClean()).isZero();
        }
    }


    @Nested
    @DisplayName("registeredAntiBotProviders()")
    class RegisteredProviders {

        @Test
        @DisplayName("returns sorted list of registered providers")
        void returnsSortedList() {
            when(detectorRegistry.registeredProviders())
                    .thenReturn(Set.of(AntiBotProvider.DATADOME, AntiBotProvider.CLOUDFLARE));

            List<AntiBotProvider> result = service.registeredAntiBotProviders();

            assertThat(result).isSorted();
            assertThat(result).contains(AntiBotProvider.CLOUDFLARE, AntiBotProvider.DATADOME);
        }
    }
}
