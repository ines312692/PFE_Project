package com.elyadata.feature.schema.domain.service;

import com.elyadata.feature.schema.domain.service.HtmlSampler.Sample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSamplerTest {

    private final HtmlSampler sampler = new HtmlSampler();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void sample_returnsEmptyForNullOrBlankHtml(String html) {
        Sample result = sampler.sample(html);

        assertThat(result.html()).isEmpty();
        assertThat(result.patterns()).isEmpty();
        assertThat(result.hasRepeatingContent()).isFalse();
    }

    @Test
    void sample_extractsTitleIntoHeadSummary() {
        String html = "<html><head><title>My Page</title></head><body><p>hi</p></body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.html()).contains("<title>My Page</title>");
    }

    @Test
    void sample_extractsH1IntoHeadSummary() {
        String html = "<html><body><h1>Main Heading</h1></body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.html()).contains("Main Heading");
    }

    @Test
    void sample_stripsMetaTagsViaCleaning() {
        // "meta" is in JUNK_TAGS, so cleanDocument removes every meta element
        // (including the description) before the head summary is built.
        String html = "<html><head>"
                + "<meta name=\"description\" content=\"a great page\">"
                + "</head><body></body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.html()).doesNotContain("a great page");
    }

    @Test
    void sample_removesScriptAndStyleTags() {
        String html = "<html><body>"
                + "<script>alert('x')</script>"
                + "<style>.a{color:red}</style>"
                + "<p>visible</p>"
                + "</body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.html()).doesNotContain("alert");
        assertThat(result.html()).doesNotContain("color:red");
    }

    @Test
    void sample_removesHtmlComments() {
        String html = "<html><body><!-- secret comment --><p>kept</p></body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.html()).doesNotContain("secret comment");
    }

    @Test
    void sample_stripsJunkAttributes() {
        String html = "<html><body>"
                + "<div class=\"card\" style=\"color:red\" tabindex=\"1\" "
                + "onclick=\"x()\" data-react-id=\"5\" aria-describedby=\"foo\">"
                + "<span>a</span><span>b</span><span>c</span>"
                + "</div></body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.html()).doesNotContain("style=");
        assertThat(result.html()).doesNotContain("tabindex");
        assertThat(result.html()).doesNotContain("onclick");
        assertThat(result.html()).doesNotContain("data-react-id");
        assertThat(result.html()).doesNotContain("aria-describedby");
    }

    @Test
    void sample_detectsRepeatingPattern() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            items.append("<li class=\"product\"><span class=\"name\">Item ")
                    .append(i).append("</span></li>");
        }
        String html = "<html><body><ul>" + items + "</ul></body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.hasRepeatingContent()).isTrue();
        assertThat(result.patterns()).isNotEmpty();
        // 5 sibling <li.product> elements should be the dominant pattern
        assertThat(result.patterns().get(0).count()).isGreaterThanOrEqualTo(3);
        assertThat(result.patterns().get(0).signature()).contains("li");
    }

    @Test
    void sample_ignoresGroupsBelowMinimumSiblings() {
        // Only 2 siblings — below MIN_SIBLINGS_FOR_PATTERN (3)
        String html = "<html><body><ul>"
                + "<li class=\"a\">one</li>"
                + "<li class=\"a\">two</li>"
                + "</ul></body></html>";

        Sample result = sampler.sample(html);

        assertThat(result.hasRepeatingContent()).isFalse();
    }

    @Test
    void sample_respectsCharacterBudget() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            items.append("<div class=\"row\">")
                    .append("x".repeat(200))
                    .append("</div>");
        }
        String html = "<html><body>" + items + "</body></html>";

        int budget = 1_000;
        Sample result = sampler.sample(html, budget);

        assertThat(result.html().length()).isLessThanOrEqualTo(budget);
    }

    @Test
    void sample_handlesHtmlWithNoBody() {
        String html = "<html><head><title>Just a head</title></head></html>";

        Sample result = sampler.sample(html);

        assertThat(result.html()).contains("Just a head");
        assertThat(result.patterns()).isEmpty();
    }
}