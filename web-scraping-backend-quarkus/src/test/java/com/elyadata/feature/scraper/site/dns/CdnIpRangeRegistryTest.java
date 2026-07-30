package com.elyadata.feature.scraper.site.dns;

import com.elyadata.feature.scraper.site.detector.config.CloudflareAntiBotConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CdnIpRangeRegistryTest {

    @Mock
    CloudflareAntiBotConfig cloudflareConfig;

    private CdnIpRangeRegistry registry() {
        lenient().when(cloudflareConfig.ipv4Ranges())
                .thenReturn(List.of("104.16.0.0/13", "172.64.0.0/13"));
        lenient().when(cloudflareConfig.ipv6Ranges())
                .thenReturn(List.of("2606:4700::/32"));
        return new CdnIpRangeRegistry(cloudflareConfig);
    }

    @Test
    void isCdnIp_trueForIpv4InCloudflareRange() {
        assertThat(registry().isCdnIp("104.16.5.5")).isTrue();
    }

    @Test
    void isCdnIp_falseForIpv4OutsideRange() {
        assertThat(registry().isCdnIp("8.8.8.8")).isFalse();
    }

    @Test
    void isCdnIp_trueForIpv6InCloudflareRange() {
        assertThat(registry().isCdnIp("2606:4700::1")).isTrue();
    }

    @Test
    void isCdnIp_falseForIpv6OutsideRange() {
        assertThat(registry().isCdnIp("2607:f8b0::1")).isFalse();
    }

    @Test
    void isCdnIp_treatsUnparseableIpAsCdn() {
        assertThat(registry().isCdnIp("not a valid ip at all")).isTrue();
    }
}
