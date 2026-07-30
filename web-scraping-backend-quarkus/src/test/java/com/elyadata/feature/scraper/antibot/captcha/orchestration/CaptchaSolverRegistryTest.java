package com.elyadata.feature.scraper.antibot.captcha.orchestration;

import com.elyadata.feature.scraper.antibot.captcha.core.*;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CaptchaSolverRegistry")
class CaptchaSolverRegistryTest {

    private CaptchaSolver mockSolver;

    @BeforeEach
    void setUp() {
        mockSolver = mock(CaptchaSolver.class);
        when(mockSolver.provider()).thenReturn(CaptchaProvider.MOCK);
        when(mockSolver.isEnabled()).thenReturn(true);
        when(mockSolver.supports(CaptchaType.RECAPTCHA_V2)).thenReturn(true);
        when(mockSolver.supports(CaptchaType.HCAPTCHA)).thenReturn(true);
        when(mockSolver.supports(CaptchaType.TURNSTILE)).thenReturn(true);
        when(mockSolver.supports(CaptchaType.IMAGE_TEXT)).thenReturn(false);
    }

    private CaptchaSolverRegistry buildRegistry(List<CaptchaSolver> solvers,
                                                 String preferred, String fallback)
            throws Exception {
        @SuppressWarnings("unchecked")
        Instance<CaptchaSolver> instance = mock(Instance.class);
        when(instance.spliterator()).thenReturn(solvers.spliterator());

        CaptchaSolverRegistry registry = new CaptchaSolverRegistry(instance);
        setField(registry, "preferredProviderName", preferred);
        setField(registry, "fallbackProviderName", fallback);
        registry.init();
        return registry;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("resolve(RECAPTCHA_V2) → returns preferred solver when usable")
    void resolve_preferredSolverUsable() throws Exception {
        CaptchaSolverRegistry registry = buildRegistry(
                List.of(mockSolver), "MOCK", "MOCK");

        Optional<CaptchaSolver> result = registry.resolve(CaptchaType.RECAPTCHA_V2);
        assertThat(result).isPresent().contains(mockSolver);
    }

    @Test
    @DisplayName("resolve(null) → returns empty")
    void resolve_null_empty() throws Exception {
        CaptchaSolverRegistry registry = buildRegistry(List.of(mockSolver), "MOCK", "MOCK");
        assertThat(registry.resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("resolve(IMAGE_TEXT) → preferred doesn't support → no fallback → empty")
    void resolve_unsupportedType_empty() throws Exception {
        CaptchaSolverRegistry registry = buildRegistry(List.of(mockSolver), "MOCK", "MOCK");
        Optional<CaptchaSolver> result = registry.resolve(CaptchaType.IMAGE_TEXT);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("resolve() uses fallback when preferred is disabled")
    void resolve_preferredDisabled_usesFallback() throws Exception {
        CaptchaSolver disabled = mock(CaptchaSolver.class);
        when(disabled.provider()).thenReturn(CaptchaProvider.TWO_CAPTCHA);
        when(disabled.isEnabled()).thenReturn(false);

        CaptchaSolver fallback = mock(CaptchaSolver.class);
        when(fallback.provider()).thenReturn(CaptchaProvider.MOCK);
        when(fallback.isEnabled()).thenReturn(true);
        when(fallback.supports(CaptchaType.TURNSTILE)).thenReturn(true);

        CaptchaSolverRegistry registry = buildRegistry(
                List.of(disabled, fallback), "TWO_CAPTCHA", "MOCK");

        Optional<CaptchaSolver> result = registry.resolve(CaptchaType.TURNSTILE);
        assertThat(result).isPresent().contains(fallback);
    }

    @Test
    @DisplayName("resolve() uses any usable solver when preferred and fallback fail")
    void resolve_usesAnyUsable() throws Exception {
        CaptchaSolver twoCapSolver = mock(CaptchaSolver.class);
        when(twoCapSolver.provider()).thenReturn(CaptchaProvider.TWO_CAPTCHA);
        when(twoCapSolver.isEnabled()).thenReturn(true);
        when(twoCapSolver.supports(CaptchaType.GEETEST)).thenReturn(true);
        CaptchaSolverRegistry registry = buildRegistry(
                List.of(twoCapSolver), "MOCK", "MOCK");

        Optional<CaptchaSolver> result = registry.resolve(CaptchaType.GEETEST);
        assertThat(result).isPresent().contains(twoCapSolver);
    }


    @Test
    @DisplayName("getPreferredProvider() returns MOCK after init with 'MOCK'")
    void getPreferredProvider_mock() throws Exception {
        CaptchaSolverRegistry registry = buildRegistry(List.of(mockSolver), "MOCK", "MOCK");
        assertThat(registry.getPreferredProvider()).isEqualTo(CaptchaProvider.MOCK);
    }

    @Test
    @DisplayName("unknown provider name in config defaults to MOCK")
    void unknownProviderName_defaultsMock() throws Exception {
        CaptchaSolverRegistry registry = buildRegistry(List.of(mockSolver), "NONEXISTENT", "MOCK");
        assertThat(registry.getPreferredProvider()).isEqualTo(CaptchaProvider.MOCK);
    }
}