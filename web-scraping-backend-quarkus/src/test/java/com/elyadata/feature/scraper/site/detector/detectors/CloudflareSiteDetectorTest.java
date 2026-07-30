package com.elyadata.feature.scraper.site.detector.detectors;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.detector.config.CloudflareAntiBotConfig;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import com.elyadata.feature.scraper.site.model.SiteDnsProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CloudflareSiteDetectorTest {

    @Mock
    CloudflareAntiBotConfig config;

    private CloudflareSiteDetector detector() {
        lenient().when(config.ipv4Ranges()).thenReturn(List.of("104.16.0.0/13"));
        lenient().when(config.ipv6Ranges()).thenReturn(List.of("2606:4700::/32"));
        return new CloudflareSiteDetector(config);
    }

    private static InetAddress ip(String s) {
        try {
            return InetAddress.getByName(s);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void providerIsCloudflare() {
        assertThat(detector().provider()).isEqualTo(AntiBotProvider.CLOUDFLARE);
    }

    @Test
    void fingerprint_emptyWhenNoSignal() {
        Optional<DetectedProtection> result = detector()
                .fingerprint(200, Map.of("content-type", List.of("text/html")), "<html>ok</html>");

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------------
    // DNS fingerprint
    // ---------------------------------------------------------------------

    @Test
    void dnsFingerprint_emptyForNullProbe() {
        assertThat(detector().dnsFingerprint(null)).isEmpty();
    }

    @Test
    void dnsFingerprint_emptyForEmptyProbe() {
        SiteDnsProbe probe = SiteDnsProbe.empty("example.com");

        assertThat(detector().dnsFingerprint(probe)).isEmpty();
    }

    @Test
    void dnsFingerprint_detectsIpInCloudflareRange() {
        SiteDnsProbe probe = new SiteDnsProbe(
                "example.com", List.of(ip("104.16.5.5")), List.of());

        Optional<DetectedProtection> result = detector().dnsFingerprint(probe);

        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo(AntiBotProvider.CLOUDFLARE);
        assertThat(result.get().confidence()).isEqualTo(0.99);
        assertThat(result.get().evidence()).contains("Cloudflare range");
    }

    @Test
    void dnsFingerprint_detectsCloudflareNameserver() {
        SiteDnsProbe probe = new SiteDnsProbe(
                "example.com", List.of(ip("8.8.8.8")), List.of("dana.ns.cloudflare.com"));

        Optional<DetectedProtection> result = detector().dnsFingerprint(probe);
        result.ifPresent(p -> {
            assertThat(p.provider()).isEqualTo(AntiBotProvider.CLOUDFLARE);
            assertThat(p.confidence()).isEqualTo(0.70);
        });
    }

    @Test
    void dnsFingerprint_emptyWhenNoCloudflareSignal() {
        SiteDnsProbe probe = new SiteDnsProbe(
                "example.com", List.of(ip("8.8.8.8")), List.of("ns1.google.com"));

        assertThat(detector().dnsFingerprint(probe)).isEmpty();
    }
}