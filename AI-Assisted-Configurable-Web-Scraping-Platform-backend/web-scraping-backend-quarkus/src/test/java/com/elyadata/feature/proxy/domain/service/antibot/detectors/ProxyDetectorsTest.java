package com.elyadata.feature.proxy.domain.service.antibot.detectors;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.ProxySource;
import com.elyadata.feature.proxy.domain.model.ProxyStatus;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Proxy AntiBot Detectors")
class ProxyDetectorsTest {

    private CloudflareProxyDetector cloudflare;
    private DataDomeProxyDetector   dataDome;

    @BeforeEach
    void setUp() {
        cloudflare = new CloudflareProxyDetector();
        dataDome   = new DataDomeProxyDetector();
    }

    private Proxy proxy(ProxyProtocol protocol) {
        return Proxy.builder()
                .id(UUID.randomUUID()).host("10.0.0.1").port(8080)
                .protocol(protocol).status(ProxyStatus.ACTIVE)
                .anonymity(ProxyAnonymity.ANONYMOUS).source(ProxySource.MANUAL)
                .successRate(1.0).totalUses(0L).successfulUses(0L).build();
    }

    // ─── provider() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("provider()")
    class ProviderIdentity {

        @Test
        @DisplayName("CloudflareProxyDetector reports CLOUDFLARE provider")
        void cloudflare_provider() {
            assertThat(cloudflare.provider()).isEqualTo(AntiBotProvider.CLOUDFLARE);
        }

        @Test
        @DisplayName("DataDomeProxyDetector reports DATADOME provider")
        void datadome_provider() {
            assertThat(dataDome.provider()).isEqualTo(AntiBotProvider.DATADOME);
        }
    }

    // ─── SOCKS rejection ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("SOCKS proxy rejection (no network required)")
    class SocksRejection {

        @ParameterizedTest(name = "Cloudflare rejects {0}")
        @CsvSource({"SOCKS4", "SOCKS5"})
        @DisplayName("Cloudflare returns UNTESTED for SOCKS proxies")
        void cloudflare_rejectsSocks(ProxyProtocol protocol) {
            DetectionResult r = cloudflare.check(proxy(protocol));
            assertThat(r.status()).isEqualTo(ProtectionStatus.UNTESTED);
            assertThat(r.provider()).isEqualTo(AntiBotProvider.CLOUDFLARE);
        }

        @ParameterizedTest(name = "DataDome rejects {0}")
        @CsvSource({"SOCKS4", "SOCKS5"})
        @DisplayName("DataDome returns UNTESTED for SOCKS proxies")
        void datadome_rejectsSocks(ProxyProtocol protocol) {
            DetectionResult r = dataDome.check(proxy(protocol));
            assertThat(r.status()).isEqualTo(ProtectionStatus.UNTESTED);
            assertThat(r.provider()).isEqualTo(AntiBotProvider.DATADOME);
        }
    }

    // ─── getProtectionStatus() static logic ──────────────────────────────────

    @Nested
    @DisplayName("getProtectionStatus() — classification logic")
    class ProtectionStatusClassification {

        @Test
        @DisplayName("blocked=true → BLOCKED regardless of other flags")
        void blocked_returnsBlocked() {
            assertThat(DataDomeProxyDetector.getProtectionStatus(200, true, true))
                    .isEqualTo(ProtectionStatus.BLOCKED);
            assertThat(DataDomeProxyDetector.getProtectionStatus(403, false, true))
                    .isEqualTo(ProtectionStatus.BLOCKED);
        }

        @Test
        @DisplayName("detected=true and 2xx status → CLEAN")
        void detected_2xx_returnsClean() {
            assertThat(DataDomeProxyDetector.getProtectionStatus(200, true, false))
                    .isEqualTo(ProtectionStatus.CLEAN);
            assertThat(DataDomeProxyDetector.getProtectionStatus(201, true, false))
                    .isEqualTo(ProtectionStatus.CLEAN);
        }

        @Test
        @DisplayName("detected=true and non-2xx status → CHALLENGED")
        void detected_non2xx_returnsChallenged() {
            assertThat(DataDomeProxyDetector.getProtectionStatus(302, true, false))
                    .isEqualTo(ProtectionStatus.CHALLENGED);
            assertThat(DataDomeProxyDetector.getProtectionStatus(403, true, false))
                    .isEqualTo(ProtectionStatus.CHALLENGED);
        }

        @Test
        @DisplayName("not detected and 2xx status → CLEAN")
        void notDetected_2xx_returnsClean() {
            assertThat(DataDomeProxyDetector.getProtectionStatus(200, false, false))
                    .isEqualTo(ProtectionStatus.CLEAN);
        }

        @Test
        @DisplayName("not detected and non-2xx status → UNTESTED")
        void notDetected_non2xx_returnsUntested() {
            assertThat(DataDomeProxyDetector.getProtectionStatus(500, false, false))
                    .isEqualTo(ProtectionStatus.UNTESTED);
            assertThat(DataDomeProxyDetector.getProtectionStatus(0, false, false))
                    .isEqualTo(ProtectionStatus.UNTESTED);
        }
    }
}