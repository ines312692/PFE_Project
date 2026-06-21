package com.elyadata.feature.visualselector.domain.service;

import com.elyadata.feature.visualselector.application.dto.HardenSelectorRequest;
import com.elyadata.feature.visualselector.domain.model.HardenedSelector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectorHardenerTest {

    private final SelectorHardener hardener = new SelectorHardener();

    private HardenedSelector harden(String html, String candidate, boolean expectList) {
        return hardener.harden(new HardenSelectorRequest(html, candidate, "https://example.com", expectList));
    }


    @Test
    void harden_returnsCandidateAsIsWhenNothingMatches() {
        String html = "<html><body><p>nothing relevant</p></body></html>";

        HardenedSelector result = harden(html, ".does-not-exist", false);

        assertThat(result.matchCount()).isZero();
        assertThat(result.unique()).isFalse();
        assertThat(result.cssSelector()).isEqualTo(".does-not-exist");
        assertThat(result.warning()).contains("could not be located");
        assertThat(result.previewValues()).isEmpty();
    }


    @Test
    void harden_prefersUniqueId() {
        String html = "<html><body>"
                + "<span id=\"product-title\" class=\"name\">Widget</span>"
                + "</body></html>";

        HardenedSelector result = harden(html, ".name", false);

        assertThat(result.cssSelector()).contains("#product-title");
        assertThat(result.unique()).isTrue();
        assertThat(result.matchCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // Prefers stable data-* attribute
    // ---------------------------------------------------------------------

    @Test
    void harden_prefersStableDataAttribute() {
        String html = "<html><body>"
                + "<div data-testid=\"price\" class=\"x9f2ab\">42</div>"
                + "</body></html>";

        HardenedSelector result = harden(html, "[data-testid]", false);

        assertThat(result.cssSelector()).contains("data-testid=\"price\"");
        assertThat(result.unique()).isTrue();
    }

    @Test
    void harden_listModeReturnsRepeatingSelector() {
        String html = "<html><body><ul>"
                + "<li class=\"item\">a</li>"
                + "<li class=\"item\">b</li>"
                + "<li class=\"item\">c</li>"
                + "</ul></body></html>";

        HardenedSelector result = harden(html, ".item", true);

        assertThat(result.matchCount()).isEqualTo(3);
        assertThat(result.cssSelector()).contains("item");
        assertThat(result.unique()).isFalse();
    }


    @Test
    void harden_collectsPreviewTextValues() {
        String html = "<html><body><ul>"
                + "<li class=\"item\">First</li>"
                + "<li class=\"item\">Second</li>"
                + "</ul></body></html>";

        HardenedSelector result = harden(html, ".item", true);

        assertThat(result.previewValues()).contains("First", "Second");
    }

    @Test
    void harden_fallsBackToHrefForEmptyText() {
        String html = "<html><body>"
                + "<a id=\"dl\" href=\"/file.pdf\"></a>"
                + "</body></html>";

        HardenedSelector result = harden(html, "#dl", false);

        assertThat(result.previewValues()).isNotEmpty();
        assertThat(result.previewValues().get(0)).contains("file.pdf");
    }


    @Test
    void harden_makesAmbiguousSelectorUnique() {
        String html = "<html><body>"
                + "<p class=\"dup\">one</p>"
                + "<p class=\"dup\">two</p>"
                + "</body></html>";

        HardenedSelector result = harden(html, ".dup", false);

        assertThat(result.matchCount()).isEqualTo(1);
        assertThat(result.unique()).isTrue();
    }


    @Test
    void harden_suggestsNameFromDataAttribute() {
        String html = "<html><body>"
                + "<div data-testid=\"Product Price\" id=\"x\">10</div>"
                + "</body></html>";

        HardenedSelector result = harden(html, "#x", false);

        assertThat(result.suggestedName()).isEqualTo("product_price");
    }

    @Test
    void harden_suggestsNameFromIdWhenNoDataAttr() {
        String html = "<html><body><h2 id=\"Main-Heading\">Hi</h2></body></html>";

        HardenedSelector result = harden(html, "#Main-Heading", false);

        assertThat(result.suggestedName()).isEqualTo("main_heading");
    }


    @Test
    void harden_producesXPathForIdSelector() {
        String html = "<html><body><span id=\"title\" class=\"n\">X</span></body></html>";

        HardenedSelector result = harden(html, ".n", false);

        assertThat(result.xpath()).contains("@id='title'");
    }

    @Test
    void harden_producesXPathForClassSelector() {
        String html = "<html><body><ul>"
                + "<li class=\"item\">a</li><li class=\"item\">b</li><li class=\"item\">c</li>"
                + "</ul></body></html>";

        HardenedSelector result = harden(html, ".item", true);

        assertThat(result.xpath()).contains("normalize-space(@class)");
    }


    @Test
    void harden_treatsForbiddenGenericAsNoMatch() {
        String html = "<html><body><div>content</div></body></html>";

        HardenedSelector result = harden(html, "body", false);

        // "body" is in FORBIDDEN_GENERIC -> safeSelect returns empty -> not located
        assertThat(result.matchCount()).isZero();
        assertThat(result.warning()).contains("could not be located");
    }

    @Test
    void harden_handlesInvalidCssGracefully() {
        String html = "<html><body><p>x</p></body></html>";

        HardenedSelector result = harden(html, "div[[[broken", false);
        assertThat(result.matchCount()).isZero();
        assertThat(result.cssSelector()).isEqualTo("div[[[broken");
    }
}