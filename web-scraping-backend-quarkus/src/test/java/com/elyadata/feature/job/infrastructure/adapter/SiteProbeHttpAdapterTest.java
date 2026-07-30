package com.elyadata.feature.job.infrastructure.adapter;

import com.elyadata.feature.job.domain.model.SiteSignal;
import com.elyadata.feature.scraper.crawler.pagination.PaginationDetector;
import com.elyadata.feature.scraper.crawler.robots.RobotsTxtGate;
import com.elyadata.feature.scraper.site.detector.SiteProtectionDetectorRegistry;
import com.elyadata.feature.scraper.site.dns.DnsResolver;
import com.elyadata.feature.scraper.site.dns.OriginIpResolver;
import com.elyadata.feature.scraper.site.scoring.ScrapingDifficultyScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SiteProbeHttpAdapter — private helper methods")
class SiteProbeHttpAdapterTest {

    @Mock PaginationDetector paginationDetector;
    @Mock OriginIpResolver originIpResolver;
    @Mock SiteProtectionDetectorRegistry protectionRegistry;
    @Mock ScrapingDifficultyScorer scorer;
    @Mock RobotsTxtGate robotsTxtGate;
    @Mock DnsResolver dnsResolver;

    SiteProbeHttpAdapter adapter;

    private static Method isSpaShellMethod;
    private static Method isHardChallengePageMethod;
    private static Method isBlockedResponseMethod;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new SiteProbeHttpAdapter(
                paginationDetector, originIpResolver, protectionRegistry,
                scorer, robotsTxtGate, dnsResolver);

        isSpaShellMethod = SiteProbeHttpAdapter.class
                .getDeclaredMethod("isSpaShell", String.class);
        isSpaShellMethod.setAccessible(true);

        isHardChallengePageMethod = SiteProbeHttpAdapter.class
                .getDeclaredMethod("isHardChallengePage", String.class, List.class);
        isHardChallengePageMethod.setAccessible(true);

        isBlockedResponseMethod = SiteProbeHttpAdapter.class
                .getDeclaredMethod("isBlockedResponse", int.class, List.class);
        isBlockedResponseMethod.setAccessible(true);
    }

    private boolean isSpaShell(String body) throws Exception {
        return (boolean) isSpaShellMethod.invoke(adapter, body);
    }

    private boolean isHardChallengePage(String body, List<SiteSignal> signals) throws Exception {
        return (boolean) isHardChallengePageMethod.invoke(adapter, body, signals);
    }

    private boolean isBlockedResponse(int status, List<SiteSignal> signals) throws Exception {
        return (boolean) isBlockedResponseMethod.invoke(adapter, status, signals);
    }

    @Nested
    @DisplayName("isSpaShell()")
    class IsSpaShell {

        @Test
        @DisplayName("detects Next.js via __next_data__")
        void nextData_detected() throws Exception {
            assertThat(isSpaShell("<script>window.__NEXT_DATA__ = {}</script>")).isTrue();
        }

        @Test
        @DisplayName("detects Next.js via _next/static")
        void nextStatic_detected() throws Exception {
            assertThat(isSpaShell("<link href='/_next/static/css/main.css'>")).isTrue();
        }

        @Test
        @DisplayName("detects Nuxt.js via __nuxt__")
        void nuxt_detected() throws Exception {
            assertThat(isSpaShell("<div id='__nuxt__'></div>")).isTrue();
        }

        @Test
        @DisplayName("detects Angular via ng-version")
        void angular_detected() throws Exception {
            assertThat(isSpaShell("<app-root ng-version='17.0.0'></app-root>")).isTrue();
        }

        @Test
        @DisplayName("detects React via data-reactroot")
        void reactRoot_detected() throws Exception {
            assertThat(isSpaShell("<div data-reactroot></div>")).isTrue();
        }

        @Test
        @DisplayName("plain HTML page is not SPA")
        void plainHtml_notSpa() throws Exception {
            assertThat(isSpaShell("<html><body><h1>Hello World</h1></body></html>")).isFalse();
        }

        @Test
        @DisplayName("small page with script and bundle keyword is detected as SPA")
        void smallScriptBundle_detected() throws Exception {
            String smallHtml = "<html><body><script src='/bundle.js'></script></body></html>";
            assertThat(isSpaShell(smallHtml)).isTrue();
        }

        @Test
        @DisplayName("large page with script and bundle but not SPA markers is not detected")
        void largeScriptBundle_notDetected() throws Exception {
            String largeHtml = "<script src='/bundle.js'></script>" + "x".repeat(15_000);
            assertThat(isSpaShell(largeHtml)).isFalse();
        }
    }

    @Nested
    @DisplayName("isHardChallengePage()")
    class IsHardChallengePage {

        @Test
        @DisplayName("detects Cloudflare 'checking your browser' page")
        void cloudflareChecking_detected() throws Exception {
            assertThat(isHardChallengePage("Checking your browser...", List.of())).isTrue();
        }

        @Test
        @DisplayName("detects Cloudflare 'just a moment' page")
        void cloudflareJustAMoment_detected() throws Exception {
            assertThat(isHardChallengePage("Just a moment...", List.of())).isTrue();
        }

        @Test
        @DisplayName("detects cf-challenge keyword")
        void cfChallenge_detected() throws Exception {
            assertThat(isHardChallengePage("<div class='cf-challenge'>...</div>", List.of())).isTrue();
        }

        @Test
        @DisplayName("detects DataDome challenge page")
        void dataDome_detected() throws Exception {
            assertThat(isHardChallengePage("<html>blocked by datadome</html>", List.of())).isTrue();
        }

        @Test
        @DisplayName("detects LIKELY_BLOCKED signal as hard challenge")
        void likelyBlocked_detected() throws Exception {
            assertThat(isHardChallengePage("normal content", List.of(SiteSignal.LIKELY_BLOCKED))).isTrue();
        }

        @Test
        @DisplayName("normal page with no challenge signals returns false")
        void normalPage_returnsFalse() throws Exception {
            assertThat(isHardChallengePage(
                    "<html><body><h1>Product list</h1></body></html>",
                    List.of())).isFalse();
        }

        @Test
        @DisplayName("attention required page is detected as challenge")
        void attentionRequired_detected() throws Exception {
            assertThat(isHardChallengePage("Attention Required! | Cloudflare", List.of())).isTrue();
        }
    }

    @Nested
    @DisplayName("isBlockedResponse()")
    class IsBlockedResponse {

        @Test
        @DisplayName("403 + CLOUDFLARE signal → blocked")
        void forbidden_cloudflare_isBlocked() throws Exception {
            assertThat(isBlockedResponse(403, List.of(SiteSignal.CLOUDFLARE))).isTrue();
        }

        @Test
        @DisplayName("503 + DATADOME signal → blocked")
        void serviceUnavailable_datadome_isBlocked() throws Exception {
            assertThat(isBlockedResponse(503, List.of(SiteSignal.DATADOME))).isTrue();
        }

        @Test
        @DisplayName("403 + LIKELY_BLOCKED signal → blocked")
        void forbidden_likelyBlocked_isBlocked() throws Exception {
            assertThat(isBlockedResponse(403, List.of(SiteSignal.LIKELY_BLOCKED))).isTrue();
        }

        @Test
        @DisplayName("403 with no anti-bot signal → not blocked")
        void forbidden_noSignal_notBlocked() throws Exception {
            assertThat(isBlockedResponse(403, List.of())).isFalse();
        }

        @Test
        @DisplayName("200 + CLOUDFLARE signal → not blocked (not a challenge status)")
        void ok_cloudflare_notBlocked() throws Exception {
            assertThat(isBlockedResponse(200, List.of(SiteSignal.CLOUDFLARE))).isFalse();
        }

        @Test
        @DisplayName("404 + AKAMAI signal → not blocked (not a challenge status)")
        void notFound_akamai_notBlocked() throws Exception {
            assertThat(isBlockedResponse(404, List.of(SiteSignal.AKAMAI))).isFalse();
        }
    }
}