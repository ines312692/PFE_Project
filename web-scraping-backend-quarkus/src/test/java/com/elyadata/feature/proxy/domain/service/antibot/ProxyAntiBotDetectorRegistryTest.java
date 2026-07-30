package com.elyadata.feature.proxy.domain.service.antibot;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.ProxySource;
import com.elyadata.feature.proxy.domain.model.ProxyStatus;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProxyAntiBotDetectorRegistry")
class ProxyAntiBotDetectorRegistryTest {

    @Mock
    private Instance<ProxyAntiBotDetector> detectorInstances;

    private final ProxyAntiBotDetector cloudflare = new StubDetector(AntiBotProvider.CLOUDFLARE);
    private final ProxyAntiBotDetector datadome = new StubDetector(AntiBotProvider.DATADOME);

    private ProxyAntiBotDetectorRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        List<ProxyAntiBotDetector> all = List.of(cloudflare, datadome);
        when(detectorInstances.spliterator()).thenReturn(all.spliterator());

        registry = new ProxyAntiBotDetectorRegistry();
        var field = ProxyAntiBotDetectorRegistry.class.getDeclaredField("detectorInstances");
        field.setAccessible(true);
        field.set(registry, detectorInstances);

        var init = ProxyAntiBotDetectorRegistry.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(registry);
    }

    private Proxy proxy() {
        return Proxy.builder()
                .id(UUID.randomUUID()).host("10.0.0.1").port(8080)
                .protocol(ProxyProtocol.HTTP).status(ProxyStatus.ACTIVE)
                .anonymity(ProxyAnonymity.ANONYMOUS).source(ProxySource.MANUAL)
                .successRate(1.0).totalUses(0L).successfulUses(0L).build();
    }


    @Nested
    @DisplayName("registeredProviders()")
    class RegisteredProviders {

        @Test
        @DisplayName("returns all registered providers")
        void returnsAllProviders() {
            assertThat(registry.registeredProviders())
                    .containsExactlyInAnyOrder(AntiBotProvider.CLOUDFLARE, AntiBotProvider.DATADOME);
        }

        @Test
        @DisplayName("size matches number of registered detectors")
        void sizeMatchesDetectorCount() {
            assertThat(registry.registeredProviders()).hasSize(2);
        }
    }


    @Nested
    @DisplayName("checkOne()")
    class CheckOne {

        @Test
        @DisplayName("returns detector result for registered provider")
        void registeredProvider_returnsResult() {
            DetectionResult result = registry.checkOne(proxy(), AntiBotProvider.CLOUDFLARE);
            assertThat(result.provider()).isEqualTo(AntiBotProvider.CLOUDFLARE);
            assertThat(result.status()).isEqualTo(ProtectionStatus.CLEAN);
        }

        @Test
        @DisplayName("returns UNTESTED for unknown provider")
        void unknownProvider_returnsUntested() {
            DetectionResult result = registry.checkOne(proxy(), AntiBotProvider.AKAMAI);
            assertThat(result.status()).isEqualTo(ProtectionStatus.UNTESTED);
        }

        @Test
        @DisplayName("returns UNREACHABLE when detector throws exception")
        void detectorThrows_returnsUnreachable() throws Exception {
            List<ProxyAntiBotDetector> crashList = List.of(new CrashingDetector(AntiBotProvider.IMPERVA));
            when(detectorInstances.spliterator()).thenReturn(crashList.spliterator());

            ProxyAntiBotDetectorRegistry crashRegistry = new ProxyAntiBotDetectorRegistry();
            var f = ProxyAntiBotDetectorRegistry.class.getDeclaredField("detectorInstances");
            f.setAccessible(true);
            f.set(crashRegistry, detectorInstances);
            var init = ProxyAntiBotDetectorRegistry.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(crashRegistry);

            assertThat(crashRegistry.checkOne(proxy(), AntiBotProvider.IMPERVA).status())
                    .isEqualTo(ProtectionStatus.UNREACHABLE);
        }
    }

    @Nested
    @DisplayName("check() — alias for checkOne()")
    class CheckAlias {

        @Test
        @DisplayName("delegates to checkOne and returns result with correct provider")
        void delegatesToCheckOne() {
            assertThat(registry.check(proxy(), AntiBotProvider.CLOUDFLARE).provider())
                    .isEqualTo(AntiBotProvider.CLOUDFLARE);
        }
    }


    @Nested
    @DisplayName("checkAll()")
    class CheckAll {

        @Test
        @DisplayName("returns one result per registered detector")
        void oneResultPerDetector() {
            assertThat(registry.checkAll(proxy())).hasSize(2);
        }

        @Test
        @DisplayName("each result has the correct provider")
        void eachResultHasProvider() {
            assertThat(registry.checkAll(proxy()))
                    .extracting(DetectionResult::provider)
                    .containsExactlyInAnyOrder(AntiBotProvider.CLOUDFLARE, AntiBotProvider.DATADOME);
        }
    }

    @Nested
    @DisplayName("duplicate detectors")
    class Duplicates {

        @Test
        @DisplayName("first registered detector is kept when same provider registered twice")
        void keepFirstOnDuplicate() throws Exception {
            List<ProxyAntiBotDetector> dupList = List.of(
                    new StubDetector(AntiBotProvider.CLOUDFLARE),
                    new StubDetector(AntiBotProvider.CLOUDFLARE));
            when(detectorInstances.spliterator()).thenReturn(dupList.spliterator());

            ProxyAntiBotDetectorRegistry dupRegistry = new ProxyAntiBotDetectorRegistry();
            var f = ProxyAntiBotDetectorRegistry.class.getDeclaredField("detectorInstances");
            f.setAccessible(true);
            f.set(dupRegistry, detectorInstances);
            var init = ProxyAntiBotDetectorRegistry.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(dupRegistry);

            assertThat(dupRegistry.registeredProviders()).hasSize(1);
        }
    }


    private static class StubDetector implements ProxyAntiBotDetector {
        private final AntiBotProvider p;

        StubDetector(AntiBotProvider p) {
            this.p = p;
        }

        @Override
        public AntiBotProvider provider() {
            return p;
        }

        @Override
        public DetectionResult check(Proxy proxy) {
            return new DetectionResult(p, ProtectionStatus.CLEAN,
                    200, false, false, 50, Instant.now(), null);
        }
    }

    private static class CrashingDetector extends StubDetector {
        CrashingDetector(AntiBotProvider p) {
            super(p);
        }

        @Override
        public DetectionResult check(Proxy proxy) {
            throw new RuntimeException("network error");
        }
    }
}