package com.elyadata.feature.scraper.browser.login;

import com.elyadata.feature.job.domain.model.SessionConfig.LoginMode;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LoginStrategyRegistry")
class LoginStrategyRegistryTest {

    private LoginStrategy strategyForm;
    private LoginStrategy strategyCookie;

    @BeforeEach
    void setUp() {
        strategyForm = mock(LoginStrategy.class);
        when(strategyForm.mode()).thenReturn(LoginMode.FORM);

        strategyCookie = mock(LoginStrategy.class);
        when(strategyCookie.mode()).thenReturn(LoginMode.COOKIE_IMPORT);
    }

    private LoginStrategyRegistry registryWith(LoginStrategy... strategies) {
        @SuppressWarnings("unchecked")
        Instance<LoginStrategy> instance = mock(Instance.class);
        when(instance.iterator()).thenReturn(List.of(strategies).iterator());
        when(instance.spliterator()).thenReturn(List.of(strategies).spliterator());
        doAnswer(inv -> {
            List.of(strategies).forEach(inv.getArgument(0));
            return null;
        }).when(instance).forEach(any());

        when(instance.spliterator()).thenReturn(List.of(strategies).spliterator());
        return new LoginStrategyRegistry(instance);
    }


    @Test
    @DisplayName("get(FORM) returns the FORM strategy")
    void get_form_returnsFormStrategy() {
        LoginStrategyRegistry registry = registryWith(strategyForm, strategyCookie);
        assertThat(registry.get(LoginMode.FORM)).isSameAs(strategyForm);
    }

    @Test
    @DisplayName("get(COOKIE_IMPORT) returns the cookie strategy")
    void get_cookieImport_returnsCookieStrategy() {
        LoginStrategyRegistry registry = registryWith(strategyForm, strategyCookie);
        assertThat(registry.get(LoginMode.COOKIE_IMPORT)).isSameAs(strategyCookie);
    }


    @Test
    @DisplayName("get() for unregistered mode throws IllegalStateException")
    void get_unregisteredMode_throws() {
        LoginStrategyRegistry registry = registryWith(strategyForm); // no COOKIE_IMPORT
        assertThatThrownBy(() -> registry.get(LoginMode.COOKIE_IMPORT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOKIE_IMPORT");
    }

    @Test
    @DisplayName("empty registry → get() always throws")
    void emptyRegistry_throws() {
        LoginStrategyRegistry registry = registryWith();
        assertThatThrownBy(() -> registry.get(LoginMode.FORM))
                .isInstanceOf(IllegalStateException.class);
    }


    @Test
    @DisplayName("duplicate mode registration — last registered wins (EnumMap put)")
    void duplicate_lastWins() {
        LoginStrategy second = mock(LoginStrategy.class);
        when(second.mode()).thenReturn(LoginMode.FORM);

        LoginStrategyRegistry registry = registryWith(strategyForm, second);
        assertThat(registry.get(LoginMode.FORM)).isSameAs(second);
    }
}