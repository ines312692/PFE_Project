package com.elyadata.feature.schema.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ActionHtmlSamplerTest {

    private final ActionHtmlSampler sampler = new ActionHtmlSampler();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void sample_returnsEmptyForNullOrBlankHtml(String html) {
        assertThat(sampler.sample(html)).isEmpty();
    }

    @Test
    void sample_capturesButtons() {
        String html = "<html><body><button id=\"buy\">Buy now</button></body></html>";

        String result = sampler.sample(html);

        assertThat(result).contains("<button");
        assertThat(result).contains("Buy now");
    }

    @Test
    void sample_capturesLinksWithHref() {
        String html = "<html><body><a href=\"/login\">Sign in</a></body></html>";

        String result = sampler.sample(html);

        assertThat(result).contains("href=\"/login\"");
        assertThat(result).contains("Sign in");
    }

    @Test
    void sample_capturesInputsAndForms() {
        String html = "<html><body><form>"
                + "<input type=\"text\" name=\"q\" placeholder=\"Search\">"
                + "</form></body></html>";

        String result = sampler.sample(html);

        assertThat(result).contains("<input");
        assertThat(result).contains("name=\"q\"");
        assertThat(result).contains("placeholder=\"Search\"");
    }

    @Test
    void sample_includesHeadSummaryWithTitleAndH1() {
        String html = "<html><head><title>Shop</title></head>"
                + "<body><h1>Welcome</h1><button>Go</button></body></html>";

        String result = sampler.sample(html);

        assertThat(result).contains("<title>Shop</title>");
        assertThat(result).contains("<h1>Welcome</h1>");
    }

    @Test
    void sample_removesScriptAndStyle() {
        String html = "<html><body>"
                + "<script>evil()</script><style>.x{}</style>"
                + "<button>Safe</button></body></html>";

        String result = sampler.sample(html);

        assertThat(result).doesNotContain("evil");
        assertThat(result).doesNotContain(".x{");
    }

    @Test
    void sample_dropsJunkAttributesKeepsWhitelisted() {
        String html = "<html><body>"
                + "<button id=\"ok\" class=\"btn\" style=\"color:red\" onmouseover=\"x()\">Click</button>"
                + "</body></html>";

        String result = sampler.sample(html);

        assertThat(result).contains("id=\"ok\"");
        assertThat(result).contains("class=\"btn\"");
        assertThat(result).doesNotContain("style=");
        assertThat(result).doesNotContain("onmouseover");
    }

    @Test
    void sample_keepsDataAndAriaAttributes() {
        String html = "<html><body>"
                + "<button data-testid=\"submit\" aria-label=\"Submit form\">Send</button>"
                + "</body></html>";

        String result = sampler.sample(html);

        assertThat(result).contains("data-testid=\"submit\"");
        assertThat(result).contains("aria-label=\"Submit form\"");
    }

    @Test
    void sample_deduplicatesIdenticalElements() {
        String html = "<html><body>"
                + "<button>Same</button>"
                + "<button>Same</button>"
                + "<button>Same</button>"
                + "</body></html>";

        String result = sampler.sample(html);

        // identical rendered lines are collapsed to one
        int occurrences = result.split("<button>Same</button>", -1).length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void sample_truncatesLongText() {
        String longText = "z".repeat(200);
        String html = "<html><body><button>" + longText + "</button></body></html>";

        String result = sampler.sample(html);

        // text is capped at MAX_TEXT_LEN (60)
        assertThat(result).doesNotContain("z".repeat(61));
    }

    @Test
    void sample_respectsCharacterBudget() {
        StringBuilder buttons = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            buttons.append("<button id=\"b").append(i).append("\">Action ").append(i).append("</button>");
        }
        String html = "<html><body>" + buttons + "</body></html>";

        int budget = 500;
        String result = sampler.sample(html, budget);

        assertThat(result.length()).isLessThanOrEqualTo(budget);
    }

    @Test
    void sample_ignoresNonInteractiveElements() {
        String html = "<html><body><p>just text</p><div>no controls</div></body></html>";

        String result = sampler.sample(html);

        // head + marker present, but no interactive lines captured
        assertThat(result).contains("INTERACTIVE ELEMENTS");
        assertThat(result).doesNotContain("just text");
    }
}
