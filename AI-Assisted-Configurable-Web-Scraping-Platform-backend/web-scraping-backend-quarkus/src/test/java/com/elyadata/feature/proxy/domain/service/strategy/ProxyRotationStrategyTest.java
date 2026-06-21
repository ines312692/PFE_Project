package com.elyadata.feature.proxy.domain.service.strategy;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProxyRotationStrategyRegistry")
class ProxyRotationStrategyRegistryTest {

    @Mock
    private Instance<ProxyRotationStrategy> strategyInstances;

    private final FastestRotationStrategy    fastest    = new FastestRotationStrategy();
    private final LeastUsedRotationStrategy  leastUsed  = new LeastUsedRotationStrategy();
    private final RandomRotationStrategy     random     = new RandomRotationStrategy();
    private final RoundRobinRotationStrategy roundRobin = new RoundRobinRotationStrategy();

    private ProxyRotationStrategyRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        List<ProxyRotationStrategy> all = List.of(fastest, leastUsed, random, roundRobin);

        when(strategyInstances.iterator()).thenReturn(all.iterator());

        registry = new ProxyRotationStrategyRegistry();

        var field = ProxyRotationStrategyRegistry.class.getDeclaredField("strategies");
        field.setAccessible(true);
        field.set(registry, strategyInstances);

        var init = ProxyRotationStrategyRegistry.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(registry);
    }

    @Nested
    @DisplayName("init() — strategy registration")
    class Init {

        @Test
        @DisplayName("registers all 4 strategies by type")
        void registersAllFour() {
            assertThat(registry.get(ProxyRotationStrategyType.FASTEST)).isSameAs(fastest);
            assertThat(registry.get(ProxyRotationStrategyType.LEAST_USED)).isSameAs(leastUsed);
            assertThat(registry.get(ProxyRotationStrategyType.RANDOM)).isSameAs(random);
            assertThat(registry.get(ProxyRotationStrategyType.ROUND_ROBIN)).isSameAs(roundRobin);
        }
    }

    @Nested
    @DisplayName("get() — known types")
    class GetKnownTypes {

        @Test
        @DisplayName("returns FASTEST strategy")
        void getFastest() {
            assertThat(registry.get(ProxyRotationStrategyType.FASTEST))
                    .isInstanceOf(FastestRotationStrategy.class);
        }

        @Test
        @DisplayName("returns LEAST_USED strategy")
        void getLeastUsed() {
            assertThat(registry.get(ProxyRotationStrategyType.LEAST_USED))
                    .isInstanceOf(LeastUsedRotationStrategy.class);
        }

        @Test
        @DisplayName("returns RANDOM strategy")
        void getRandom() {
            assertThat(registry.get(ProxyRotationStrategyType.RANDOM))
                    .isInstanceOf(RandomRotationStrategy.class);
        }

        @Test
        @DisplayName("returns ROUND_ROBIN strategy")
        void getRoundRobin() {
            assertThat(registry.get(ProxyRotationStrategyType.ROUND_ROBIN))
                    .isInstanceOf(RoundRobinRotationStrategy.class);
        }
    }

    @Nested
    @DisplayName("get() — fallback to RANDOM")
    class GetFallback {

        @Test
        @DisplayName("returns RANDOM when type is null")
        void nullTypeFallsBackToRandom() {
            assertThat(registry.get(null)).isInstanceOf(RandomRotationStrategy.class);
        }

        @Test
        @DisplayName("returns RANDOM when requested type is not registered")
        void unknownTypeFallsBackToRandom() throws Exception {
            // sparse registry — only RANDOM registered
            when(strategyInstances.iterator()).thenReturn(List.<ProxyRotationStrategy>of(random).iterator());

            ProxyRotationStrategyRegistry sparse = new ProxyRotationStrategyRegistry();
            var field = ProxyRotationStrategyRegistry.class.getDeclaredField("strategies");
            field.setAccessible(true);
            field.set(sparse, strategyInstances);

            var init = ProxyRotationStrategyRegistry.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(sparse);
            assertThat(sparse.get(ProxyRotationStrategyType.FASTEST))
                    .isInstanceOf(RandomRotationStrategy.class);
        }
    }

    @Nested
    @DisplayName("retrieved strategies produce valid orderByClause()")
    class OrderByClauses {

        @Test
        @DisplayName("FASTEST clause contains latency_ms")
        void fastestClause() {
            assertThat(registry.get(ProxyRotationStrategyType.FASTEST).orderByClause())
                    .isNotBlank().containsIgnoringCase("latency_ms");
        }

        @Test
        @DisplayName("LEAST_USED clause contains total_uses")
        void leastUsedClause() {
            assertThat(registry.get(ProxyRotationStrategyType.LEAST_USED).orderByClause())
                    .isNotBlank().containsIgnoringCase("total_uses");
        }

        @Test
        @DisplayName("RANDOM clause contains RANDOM()")
        void randomClause() {
            assertThat(registry.get(ProxyRotationStrategyType.RANDOM).orderByClause())
                    .isNotBlank().containsIgnoringCase("RANDOM()");
        }

        @Test
        @DisplayName("ROUND_ROBIN clause contains last_used_at")
        void roundRobinClause() {
            assertThat(registry.get(ProxyRotationStrategyType.ROUND_ROBIN).orderByClause())
                    .isNotBlank().containsIgnoringCase("last_used_at");
        }
    }
}