package com.elyadata.feature.scraper.crawler.url;

import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlFilterTest {

    private static final String ROOT = "https://shop.example.com/";

    private UrlFilter filterWith(String include, String exclude, boolean sameDomain) {
        InternalScrapeRequest req = mock(InternalScrapeRequest.class);
        when(req.getTargetUrl()).thenReturn(ROOT);
        when(req.getUrlIncludePatterns()).thenReturn(include);
        when(req.getUrlExcludePatterns()).thenReturn(exclude);
        when(req.isSameDomainOnly()).thenReturn(sameDomain);
        return new UrlFilter(req);
    }

    @Nested
    class SameDomainOnly {

        @Test
        void same_domain_url_is_accepted_when_flag_enabled() {
            UrlFilter filter = filterWith(null, null, true);
            assertThat(filter.accepts("https://shop.example.com/products")).isTrue();
        }

        @Test
        void www_variant_of_same_domain_is_accepted() {
            UrlFilter filter = filterWith(null, null, true);
            assertThat(filter.accepts("https://www.shop.example.com/products")).isTrue();
        }

        @Test
        void different_domain_is_rejected_when_flag_enabled() {
            UrlFilter filter = filterWith(null, null, true);
            assertThat(filter.accepts("https://other.com/page")).isFalse();
        }

        @Test
        void different_domain_is_accepted_when_flag_disabled() {
            UrlFilter filter = filterWith(null, null, false);
            assertThat(filter.accepts("https://other.com/page")).isTrue();
        }
    }

    @Nested
    class IncludePatterns {

        @Test
        void url_matching_include_pattern_is_accepted() {
            UrlFilter filter = filterWith("/products/.*", null, false);
            assertThat(filter.accepts("https://shop.example.com/products/item-1")).isTrue();
        }

        @Test
        void url_not_matching_include_pattern_is_rejected() {
            UrlFilter filter = filterWith("/products/.*", null, false);
            assertThat(filter.accepts("https://shop.example.com/blog/post-1")).isFalse();
        }

        @Test
        void no_include_pattern_accepts_all_urls() {
            UrlFilter filter = filterWith(null, null, false);
            assertThat(filter.accepts("https://anywhere.com/whatever")).isTrue();
        }

        @Test
        void empty_include_pattern_accepts_all_urls() {
            UrlFilter filter = filterWith("", null, false);
            assertThat(filter.accepts("https://shop.example.com/any")).isTrue();
        }

        @Test
        void multiple_include_patterns_csv_any_match_accepts() {
            UrlFilter filter = filterWith("/products/.*, /blog/.*", null, false);
            assertThat(filter.accepts("https://shop.example.com/blog/post-1")).isTrue();
            assertThat(filter.accepts("https://shop.example.com/products/item")).isTrue();
        }

        @Test
        void multiple_include_patterns_none_match_rejects() {
            UrlFilter filter = filterWith("/products/.*, /blog/.*", null, false);
            assertThat(filter.accepts("https://shop.example.com/about")).isFalse();
        }
    }

    @Nested
    class ExcludePatterns {

        @Test
        void url_matching_exclude_pattern_is_rejected() {
            UrlFilter filter = filterWith(null, "/admin/.*", false);
            assertThat(filter.accepts("https://shop.example.com/admin/dashboard")).isFalse();
        }

        @Test
        void url_not_matching_exclude_pattern_is_accepted() {
            UrlFilter filter = filterWith(null, "/admin/.*", false);
            assertThat(filter.accepts("https://shop.example.com/products")).isTrue();
        }

        @Test
        void multiple_exclude_patterns_csv_any_match_rejects() {
            UrlFilter filter = filterWith(null, "/admin/.*, /login", false);
            assertThat(filter.accepts("https://shop.example.com/login")).isFalse();
            assertThat(filter.accepts("https://shop.example.com/admin/users")).isFalse();
        }

        @Test
        void no_exclude_pattern_rejects_nothing() {
            UrlFilter filter = filterWith(null, null, false);
            assertThat(filter.accepts("https://shop.example.com/admin")).isTrue();
        }
    }

    @Nested
    class GlobPatterns {

        @Test
        void single_star_glob_matches_within_segment() {
            UrlFilter filter = filterWith("/products/*.html", null, false);
            assertThat(filter.accepts("https://shop.example.com/products/item.html")).isTrue();
        }

        @Test
        void single_star_glob_does_not_cross_slash() {
            UrlFilter filter = filterWith("/products/*.html", null, false);
            assertThat(filter.accepts("https://shop.example.com/products/sub/item.html")).isFalse();
        }

        @Test
        void double_star_glob_matches_across_segments() {
            UrlFilter filter = filterWith("**/item.html", null, false);
            assertThat(filter.accepts("https://shop.example.com/products/sub/item.html")).isTrue();
        }

        @Test
        void question_mark_glob_matches_single_char() {
            UrlFilter filter = filterWith("/page?", null, false);
            assertThat(filter.accepts("https://shop.example.com/page1")).isTrue();
            assertThat(filter.accepts("https://shop.example.com/page")).isFalse();
        }

        @Test
        void dot_in_glob_is_treated_as_literal() {
            UrlFilter filter = filterWith("*.html", null, false);
            assertThat(filter.accepts("https://shop.example.com/item.html")).isTrue();
            assertThat(filter.accepts("https://shop.example.com/itemXhtml")).isFalse();
        }
    }

    @Nested
    class IncludeAndExclude {

        @Test
        void url_matching_include_but_also_exclude_is_rejected() {
            UrlFilter filter = filterWith("/products/.*", "/products/internal/.*", false);
            assertThat(filter.accepts("https://shop.example.com/products/item-1")).isTrue();
            assertThat(filter.accepts("https://shop.example.com/products/internal/secret")).isFalse();
        }
    }

    @Nested
    class InvalidPatterns {

        @Test
        void invalid_regex_in_include_is_silently_skipped() {
            UrlFilter filter = filterWith("[invalid(", null, false);
            assertThat(filter.accepts("https://shop.example.com/any")).isTrue();
        }

        @Test
        void invalid_regex_in_exclude_is_silently_skipped() {
            UrlFilter filter = filterWith(null, "[invalid(", false);
            assertThat(filter.accepts("https://shop.example.com/any")).isTrue();
        }
    }
}