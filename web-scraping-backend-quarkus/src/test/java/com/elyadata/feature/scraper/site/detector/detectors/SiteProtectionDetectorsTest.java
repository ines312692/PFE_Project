package com.elyadata.feature.scraper.site.detector.detectors;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


class SiteProtectionDetectorsTest {

    private static Map<String, List<String>> headers(String key, String value) {
        return Map.of(key, List.of(value));
    }


    @Test
    void dataDome_providerIsDatadome() {
        assertThat(new DataDomeSiteDetector().provider()).isEqualTo(AntiBotProvider.DATADOME);
    }

    @Test
    void dataDome_emptyWhenNoSignal() {
        Optional<DetectedProtection> result = new DataDomeSiteDetector()
                .fingerprint(200, Map.of("content-type", List.of("text/html")), "<html>ok</html>");

        assertThat(result).isEmpty();
    }

    @Test
    void dataDome_emptyForNullBodyAndNoHeaders() {
        Optional<DetectedProtection> result = new DataDomeSiteDetector()
                .fingerprint(200, Map.of(), null);

        assertThat(result).isEmpty();
    }


    @Test
    void akamai_providerIsAkamai() {
        assertThat(new AkamaiSiteDetector().provider()).isEqualTo(AntiBotProvider.AKAMAI);
    }

    @Test
    void akamai_emptyWhenNoSignal() {
        Optional<DetectedProtection> result = new AkamaiSiteDetector()
                .fingerprint(200, Map.of("content-type", List.of("text/html")), "<html>ok</html>");

        assertThat(result).isEmpty();
    }

    @Test
    void akamai_detectsViaServerHeaderValueAkamaiGhost() {
        Optional<DetectedProtection> result = new AkamaiSiteDetector()
                .fingerprint(403, headers("Server", "AkamaiGHost"), "");
        result.ifPresent(p -> assertThat(p.provider()).isEqualTo(AntiBotProvider.AKAMAI));
    }


    @Test
    void captcha_providerIsHcaptcha() {
        assertThat(new CaptchaSiteDetector().provider()).isEqualTo(AntiBotProvider.HCAPTCHA);
    }

    @Test
    void captcha_emptyForNullBody() {
        assertThat(new CaptchaSiteDetector().fingerprint(200, Map.of(), null)).isEmpty();
    }

    @Test
    void captcha_emptyForEmptyBody() {
        assertThat(new CaptchaSiteDetector().fingerprint(200, Map.of(), "")).isEmpty();
    }

    @Test
    void captcha_emptyForPlainPage() {
        Optional<DetectedProtection> result = new CaptchaSiteDetector()
                .fingerprint(200, Map.of(), "<html><body>just content</body></html>");

        assertThat(result).isEmpty();
    }


    @Test
    void perimeterX_providerIsPerimeterX() {
        assertThat(new PerimeterXSiteDetector().provider()).isEqualTo(AntiBotProvider.PERIMETER_X);
    }

    @Test
    void perimeterX_alwaysReturnsAPerimeterXResult() {
        Optional<DetectedProtection> result = new PerimeterXSiteDetector()
                .fingerprint(200, Map.of("content-type", List.of("text/html")), "<html>ok</html>");

        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo(AntiBotProvider.PERIMETER_X);
    }
}
