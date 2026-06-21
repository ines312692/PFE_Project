package com.elyadata.feature.scraper.crawler.url;

import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LinkHarvester")
class LinkHarvesterTest {

    private static final String BASE = "https://shop.example.com/";

    @Nested
    @DisplayName("guard conditions")
    class Guards {

        @Test
        @DisplayName("returns empty set when html is null")
        void nullHtml() {
            PageSnapshot snap = snap(null);
            assertThat(LinkHarvester.harvest(snap)).isEmpty();
        }

        @Test
        @DisplayName("returns empty set when html is blank")
        void blankHtml() {
            PageSnapshot snap = snap("   ");
            assertThat(LinkHarvester.harvest(snap)).isEmpty();
        }

        @Test
        @DisplayName("returns empty set when page has no anchors")
        void noAnchors() {
            PageSnapshot snap = snap("<html><body><p>no links here</p></body></html>");
            assertThat(LinkHarvester.harvest(snap)).isEmpty();
        }
    }

    @Nested
    @DisplayName("unnavigable href filtering")
    class UnnavigableFiltering {

        @Test
        @DisplayName("filters out mailto: links")
        void filterMailto() {
            String html = "<a href='mailto:user@example.com'>email</a>" +
                    "<a href='/products'>Shop</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
            assertThat(links.iterator().next()).contains("/products");
        }

        @Test
        @DisplayName("filters out tel: links")
        void filterTel() {
            String html = "<a href='tel:+1234567890'>Call</a>" +
                    "<a href='/about'>About</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
        }

        @Test
        @DisplayName("filters out javascript: links")
        void filterJavascript() {
            String html = "<a href='javascript:void(0)'>Click</a>" +
                    "<a href='/home'>Home</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
        }

        @Test
        @DisplayName("filters out sms: links")
        void filterSms() {
            String html = "<a href='sms:+1234'>Text us</a>" +
                    "<a href='/contact'>Contact</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
        }

        @Test
        @DisplayName("filters out bare # anchor")
        void filterBareHash() {
            String html = "<a href='#'>Top</a>" +
                    "<a href='/page2'>Page 2</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
        }

        @Test
        @DisplayName("filters out in-page fragment links (#section)")
        void filterFragment() {
            String html = "<a href='#section-1'>Section</a>" +
                    "<a href='/page3'>Page 3</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
        }

        @Test
        @DisplayName("filters out all unnavigable types together")
        void filterAllUnnavigable() {
            String html = "<a href='mailto:a@b.com'>M</a>" +
                    "<a href='tel:123'>T</a>" +
                    "<a href='javascript:;'>J</a>" +
                    "<a href='#'>H</a>" +
                    "<a href='#id'>I</a>" +
                    "<a href='/valid'>Valid</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
            assertThat(links.iterator().next()).contains("/valid");
        }
    }

    @Nested
    @DisplayName("content zone selector priority")
    class ContentZone {

        @Test
        @DisplayName("prefers <main> links over global anchors")
        void prefersMain() {
            String html = "<html><body>" +
                    "<header><a href='/nav-link'>Nav</a></header>" +
                    "<main><a href='/main-link'>Main</a></main>" +
                    "</body></html>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).anyMatch(l -> l.contains("/main-link"));
            assertThat(links).noneMatch(l -> l.contains("/nav-link"));
        }

        @Test
        @DisplayName("prefers [role=main] when no <main> element present")
        void prefersRoleMain() {
            String html = "<html><body>" +
                    "<nav><a href='/nav'>Nav</a></nav>" +
                    "<div role='main'><a href='/role-main-link'>Content</a></div>" +
                    "</body></html>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).anyMatch(l -> l.contains("/role-main-link"));
            assertThat(links).noneMatch(l -> l.contains("/nav"));
        }

        @Test
        @DisplayName("prefers <article> when no main element present")
        void prefersArticle() {
            String html = "<html><body>" +
                    "<aside><a href='/sidebar'>Side</a></aside>" +
                    "<article><a href='/article-link'>Article</a></article>" +
                    "</body></html>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).anyMatch(l -> l.contains("/article-link"));
            assertThat(links).noneMatch(l -> l.contains("/sidebar"));
        }

        @Test
        @DisplayName("prefers .product-card links when present")
        void prefersProductCard() {
            String html = "<html><body>" +
                    "<footer><a href='/footer'>Footer</a></footer>" +
                    "<div class='product-card'><a href='/product-1'>Product</a></div>" +
                    "</body></html>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).anyMatch(l -> l.contains("/product-1"));
            assertThat(links).noneMatch(l -> l.contains("/footer"));
        }

        @Test
        @DisplayName("falls back to all a[href] when no content zone matches")
        void fallbackToAllAnchors() {
            String html = "<html><body>" +
                    "<div><a href='/link-a'>A</a></div>" +
                    "<div><a href='/link-b'>B</a></div>" +
                    "</body></html>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(2);
            assertThat(links).anyMatch(l -> l.contains("/link-a"));
            assertThat(links).anyMatch(l -> l.contains("/link-b"));
        }
    }

    @Nested
    @DisplayName("normalization and deduplication")
    class NormalizationAndDedup {

        @Test
        @DisplayName("resolves relative links against base URL")
        void resolvesRelativeLinks() {
            String html = "<a href='/products/item-1'>Item</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
            assertThat(links.iterator().next())
                    .startsWith("https://shop.example.com");
        }

        @Test
        @DisplayName("deduplicates identical links")
        void deduplicates() {
            String html = "<a href='/page'>P</a>" +
                    "<a href='/page'>P again</a>" +
                    "<a href='/page'>P third</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
        }

        @Test
        @DisplayName("strips tracking params via UrlNormalizer")
        void stripsTrackingParams() {
            String html = "<a href='/product?id=1&utm_source=google&utm_medium=cpc'>Item</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
            assertThat(links.iterator().next())
                    .doesNotContain("utm_source")
                    .doesNotContain("utm_medium")
                    .contains("id=1");
        }

        @Test
        @DisplayName("keeps meaningful query params after tracking strip")
        void keepsMeaningfulParams() {
            String html = "<a href='/search?q=shoes&ref=banner'>Shoes</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
            assertThat(links.iterator().next())
                    .contains("q=shoes")
                    .doesNotContain("ref=");
        }

        @Test
        @DisplayName("ignores non-http(s) scheme absolute links")
        void ignoresNonHttpScheme() {
            String html = "<a href='ftp://files.example.com/file.zip'>FTP</a>" +
                    "<a href='/valid'>Valid</a>";
            Set<String> links = LinkHarvester.harvest(snap(html));
            assertThat(links).hasSize(1);
            assertThat(links.iterator().next()).contains("/valid");
        }
    }

    @Nested
    @DisplayName("MAX_LINKS_PER_PAGE cap")
    class MaxLinksCap {

        @Test
        @DisplayName("stops collecting after 200 unique links")
        void capsAt200() {
            String links = IntStream.rangeClosed(1, 250)
                    .mapToObj(i -> "<a href='/item-" + i + "'>Item " + i + "</a>")
                    .collect(Collectors.joining("\n"));
            String html = "<html><body><main>" + links + "</main></body></html>";

            Set<String> result = LinkHarvester.harvest(snap(html));
            assertThat(result).hasSize(200);
        }

        @Test
        @DisplayName("collects all links when under the 200 cap")
        void collectsAllWhenUnderCap() {
            String links = IntStream.rangeClosed(1, 50)
                    .mapToObj(i -> "<a href='/item-" + i + "'>Item " + i + "</a>")
                    .collect(Collectors.joining("\n"));
            String html = "<html><body><main>" + links + "</main></body></html>";

            Set<String> result = LinkHarvester.harvest(snap(html));
            assertThat(result).hasSize(50);
        }
    }

    private static PageSnapshot snap(String html) {
        return PageSnapshot.builder().url(BASE).html(html).build();
    }
}
