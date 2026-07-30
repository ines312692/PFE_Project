package com.elyadata.feature.scraper.crawler.url;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UrlNormalizerTest {

    private static final String BASE = "https://www.example.com";

    @Nested
    class Normalize_EmptyInput {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        void returns_empty_for_blank_raw(String raw) {
            assertThat(UrlNormalizer.normalize(raw, BASE)).isEmpty();
        }
    }

    @Nested
    class Normalize_Scheme {

        @Test
        void http_url_is_accepted() {
            Optional<String> result = UrlNormalizer.normalize("http://example.com/page", BASE);
            assertThat(result).isPresent();
        }

        @Test
        void https_url_is_accepted() {
            Optional<String> result = UrlNormalizer.normalize("https://example.com/page", BASE);
            assertThat(result).isPresent();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "mailto:user@example.com",
                "ftp://files.example.com",
                "javascript:void(0)",
                "data:text/html,<h1>hi</h1>"
        })
        void non_http_schemes_are_rejected(String url) {
            assertThat(UrlNormalizer.normalize(url, BASE)).isEmpty();
        }
    }

    @Nested
    class Normalize_Host {

        @Test
        void www_prefix_is_stripped() {
            Optional<String> result = UrlNormalizer.normalize("https://www.example.com/page", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).doesNotContain("www.");
        }

        @Test
        void host_is_lowercased() {
            Optional<String> result = UrlNormalizer.normalize("https://EXAMPLE.COM/page", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).contains("example.com");
            assertThat(result.get()).doesNotContain("EXAMPLE");
        }
    }

    @Nested
    class Normalize_DefaultPorts {

        @Test
        void http_port_80_is_removed() {
            Optional<String> result = UrlNormalizer.normalize("http://example.com:80/page", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).doesNotContain(":80");
        }

        @Test
        void https_port_443_is_removed() {
            Optional<String> result = UrlNormalizer.normalize("https://example.com:443/page", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).doesNotContain(":443");
        }

        @Test
        void non_default_port_is_preserved() {
            Optional<String> result = UrlNormalizer.normalize("https://example.com:8080/page", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).contains(":8080");
        }
    }

    @Nested
    class Normalize_RelativeUrls {

        @Test
        void relative_path_is_resolved_against_base() {
            Optional<String> result = UrlNormalizer.normalize("/products/item-1", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).contains("example.com");
            assertThat(result.get()).contains("/products/item-1");
        }

        @Test
        void relative_path_without_slash_is_resolved() {
            Optional<String> result = UrlNormalizer.normalize("about", "https://example.com/en/");
            assertThat(result).isPresent();
            assertThat(result.get()).contains("example.com");
        }

        @Test
        void empty_path_gets_slash() {
            Optional<String> result = UrlNormalizer.normalize("https://example.com", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).endsWith("/");
        }
    }


    @Nested
    class Normalize_QueryString {

        @Test
        void clean_query_params_are_preserved() {
            Optional<String> result = UrlNormalizer.normalize(
                    "https://example.com/search?q=laptop&page=2", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).contains("q=laptop");
            assertThat(result.get()).contains("page=2");
        }

        @Test
        void query_params_are_sorted_alphabetically() {
            Optional<String> result = UrlNormalizer.normalize(
                    "https://example.com/search?page=2&q=laptop", BASE);
            assertThat(result).isPresent();
            // 'p' < 'q' alphabetically
            String url = result.get();
            assertThat(url.indexOf("page")).isLessThan(url.indexOf("q="));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "gclid", "fbclid", "mc_cid", "mc_eid", "_ga", "ref", "ref_src"
        })
        void tracking_params_are_stripped(String trackingParam) {
            String raw = "https://example.com/page?" + trackingParam + "=abc123";
            Optional<String> result = UrlNormalizer.normalize(raw, BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).doesNotContain(trackingParam);
        }

        @Test
        void mixed_tracking_and_clean_params_keeps_only_clean() {
            Optional<String> result = UrlNormalizer.normalize(
                    "https://example.com/page?q=test&utm_source=google&page=1", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).contains("q=test");
            assertThat(result.get()).contains("page=1");
            assertThat(result.get()).doesNotContain("utm_source");
        }

        @Test
        void url_with_only_tracking_params_has_no_query_string() {
            Optional<String> result = UrlNormalizer.normalize(
                    "https://example.com/page?utm_source=google&gclid=xyz", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).doesNotContain("?");
        }
    }

    @Nested
    class Normalize_Fragment {

        @Test
        void fragment_is_stripped() {
            Optional<String> result = UrlNormalizer.normalize(
                    "https://example.com/page#section-2", BASE);
            assertThat(result).isPresent();
            assertThat(result.get()).doesNotContain("#");
        }
    }

    @Nested
    class ExtractDomain {

        @Test
        void extracts_domain_from_https_url() {
            assertThat(UrlNormalizer.extractDomain("https://www.example.com/path"))
                    .isEqualTo("example.com");
        }

        @Test
        void extracts_domain_from_http_url() {
            assertThat(UrlNormalizer.extractDomain("http://shop.example.com/items"))
                    .isEqualTo("shop.example.com");
        }

        @Test
        void www_prefix_is_stripped_in_domain_extraction() {
            assertThat(UrlNormalizer.extractDomain("https://www.example.com"))
                    .isEqualTo("example.com");
        }

        @Test
        void domain_is_lowercased() {
            assertThat(UrlNormalizer.extractDomain("https://EXAMPLE.COM/page"))
                    .isEqualTo("example.com");
        }

        @Test
        void invalid_url_returns_original_input() {
            assertThat(UrlNormalizer.extractDomain("not-a-url")).isEqualTo("not-a-url");
        }
    }
}
