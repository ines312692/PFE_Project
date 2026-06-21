package com.elyadata.feature.visualselector.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void sanitize_returnsEmptyForNullOrBlank(String html) {
        assertThat(sanitizer.sanitize(html, "https://example.com")).isEmpty();
    }

    @Test
    void sanitize_removesScriptTags() {
        String html = "<html><body><script>alert('x')</script><p>kept</p></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).doesNotContain("alert");
        assertThat(out).doesNotContain("<script");
        assertThat(out).contains("kept");
    }

    @Test
    void sanitize_removesNoscriptTags() {
        String html = "<html><body><noscript>fallback</noscript><p>real</p></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).doesNotContain("<noscript");
        assertThat(out).contains("real");
    }

    @Test
    void sanitize_stripsInlineEventHandlers() {
        String html = "<html><body>"
                + "<button onclick=\"doEvil()\" onmouseover=\"x()\">Click</button>"
                + "</body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).doesNotContain("onclick");
        assertThat(out).doesNotContain("onmouseover");
        assertThat(out).contains("Click");
    }

    @Test
    void sanitize_removesJavascriptHrefs() {
        String html = "<html><body><a href=\"javascript:steal()\">link</a></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).doesNotContain("javascript:");
    }

    @Test
    void sanitize_removesJavascriptAction() {
        String html = "<html><body><form action=\"javascript:go()\"></form></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).doesNotContain("javascript:");
    }

    @Test
    void sanitize_absolutisesRelativeHref() {
        String html = "<html><body><a href=\"/about\">about</a></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).contains("https://example.com/about");
    }

    @Test
    void sanitize_absolutisesRelativeSrc() {
        String html = "<html><body><img src=\"/img/logo.png\"></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).contains("https://example.com/img/logo.png");
    }

    @Test
    void sanitize_promotesDataSrcToSrc() {
        String html = "<html><body><img data-src=\"/lazy/pic.jpg\"></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).contains("https://example.com/lazy/pic.jpg");
        assertThat(out).contains("src=");
    }

    @Test
    void sanitize_removesCspMetaTag() {
        String html = "<html><head>"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'\">"
                + "</head><body><p>x</p></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out.toLowerCase()).doesNotContain("content-security-policy");
    }

    @Test
    void sanitize_keepsOtherMetaTags() {
        String html = "<html><head>"
                + "<meta http-equiv=\"refresh\" content=\"30\">"
                + "</head><body></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out.toLowerCase()).contains("refresh");
    }

    @Test
    void sanitize_removesBaseTag() {
        String html = "<html><head><base href=\"https://other.com/\"></head>"
                + "<body><p>x</p></body></html>";

        String out = sanitizer.sanitize(html, "https://example.com");

        assertThat(out).doesNotContain("<base");
    }

    @Test
    void sanitize_handlesNullFinalUrl() {
        String html = "<html><body><p>content here long enough</p></body></html>";

        String out = sanitizer.sanitize(html, null);

        assertThat(out).contains("content here");
    }
}
