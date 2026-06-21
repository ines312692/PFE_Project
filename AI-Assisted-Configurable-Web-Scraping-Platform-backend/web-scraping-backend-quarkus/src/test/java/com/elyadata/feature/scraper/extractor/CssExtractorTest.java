package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.FieldConfig;
import com.elyadata.feature.scraper.model.FieldType;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CssExtractor")
class CssExtractorTest {

    private CssExtractor extractor;

    @Mock private Page playwrightPage;
    @Mock private ElementHandle element;
    @Mock private LiveBrowserContext browserContext;

    interface SeleniumDriver extends WebDriver, JavascriptExecutor {}
    @Mock private SeleniumDriver seleniumDriver;

    @BeforeEach
    void setUp() {
        extractor = new CssExtractor();
    }


    private PageSnapshot snapshotWithPlaywright() {
        when(browserContext.isPlaywright()).thenReturn(true);
        when(browserContext.getPage()).thenReturn(playwrightPage);
        return PageSnapshot.builder()
                .url("https://example.com")
                .browserContext(browserContext)
                .build();
    }

    private PageSnapshot snapshotWithSelenium() {
        when(browserContext.isPlaywright()).thenReturn(false);
        when(browserContext.isSelenium()).thenReturn(true);
        when(browserContext.getDriver()).thenReturn(seleniumDriver);
        return PageSnapshot.builder()
                .url("https://example.com")
                .browserContext(browserContext)
                .build();
    }

    private static FieldConfig textField(String name, String selector) {
        return FieldConfig.builder().name(name).selector(selector)
                .type(FieldType.TEXT).build();
    }

    private static InternalScrapeRequest requestWithFields(List<FieldConfig> fields) {
        return InternalScrapeRequest.builder()
                .targetUrl("https://example.com")
                .fields(fields)
                .build();
    }

    @Test
    @DisplayName("mode() returns CSS_SELECTOR")
    void mode_returnsCssSelector() {
        assertThat(extractor.mode()).isEqualTo(ExtractionMode.CSS_SELECTOR);
    }

    @Nested
    @DisplayName("Guard clauses")
    class GuardClauses {

        @Test
        @DisplayName("returns empty when request has no fields")
        void extract_noFields_returnsEmpty() {
            var snapshot = snapshotWithPlaywright();
            var request = requestWithFields(List.of());

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }

        @Test
        @DisplayName("returns empty when snapshot has no browser context")
        void extract_noBrowserContext_returnsEmpty() {
            var snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .browserContext(null)
                    .build();
            var request = requestWithFields(List.of(textField("name", ".name")));

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }

        @Test
        @DisplayName("returns empty when no elements matched (Playwright)")
        void extract_playwright_noElementsMatched_returnsEmpty() {
            var snapshot = snapshotWithPlaywright();
            when(playwrightPage.querySelectorAll(anyString())).thenReturn(List.of());
            var request = requestWithFields(List.of(textField("name", ".missing")));

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Playwright path")
    class PlaywrightPath {

        @Test
        @DisplayName("extracts TEXT type using innerText()")
        void extract_textType_usesInnerText() {
            var snapshot = snapshotWithPlaywright();
            when(playwrightPage.querySelectorAll(".name")).thenReturn(List.of(element));
            when(element.innerText()).thenReturn("  Alice  ");

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", ".name"))));

            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst().get("name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("extracts ATTRIBUTE type using getAttribute()")
        void extract_attributeType_usesGetAttribute() {
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("link").selector("a").type(FieldType.ATTRIBUTE)
                    .attribute("data-id").build();

            when(playwrightPage.querySelectorAll("a")).thenReturn(List.of(element));
            when(element.getAttribute("data-id")).thenReturn("42");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("link")).isEqualTo("42");
        }

        @Test
        @DisplayName("extracts URL type using href attribute")
        void extract_urlType_usesHrefAttribute() {
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("url").selector("a").type(FieldType.URL).build();

            when(playwrightPage.querySelectorAll("a")).thenReturn(List.of(element));
            when(element.getAttribute("href")).thenReturn("https://target.com/page");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("url")).isEqualTo("https://target.com/page");
        }

        @Test
        @DisplayName("extracts IMAGE type using src attribute")
        void extract_imageType_usesSrcAttribute() {
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("img").selector("img").type(FieldType.IMAGE).build();

            when(playwrightPage.querySelectorAll("img")).thenReturn(List.of(element));
            when(element.getAttribute("src")).thenReturn("https://cdn.example.com/img.jpg");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("img"))
                    .isEqualTo("https://cdn.example.com/img.jpg");
        }

        @Test
        @DisplayName("extracts HTML type using innerHTML()")
        void extract_htmlType_usesInnerHtml() {
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("content").selector(".body").type(FieldType.HTML).build();

            when(playwrightPage.querySelectorAll(".body")).thenReturn(List.of(element));
            when(element.innerHTML()).thenReturn("<b>Bold</b> text");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("content")).isEqualTo("<b>Bold</b> text");
        }

        @Test
        @DisplayName("builds one record per matched element (multi-element)")
        void extract_multipleElements_buildsMultipleRecords() {
            var snapshot = snapshotWithPlaywright();
            ElementHandle el1 = mock(ElementHandle.class);
            ElementHandle el2 = mock(ElementHandle.class);
            when(el1.innerText()).thenReturn("Alice");
            when(el2.innerText()).thenReturn("Bob");
            when(playwrightPage.querySelectorAll(".name")).thenReturn(List.of(el1, el2));

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", ".name"))));

            assertThat(output.records()).hasSize(2);
            assertThat(output.records().get(0).get("name")).isEqualTo("Alice");
            assertThat(output.records().get(1).get("name")).isEqualTo("Bob");
        }

        @Test
        @DisplayName("row is filtered out when ATTRIBUTE value is null (blank → buildRecords skips it)")
        void extract_attributeNull_rowFiltered() {
            // Production behavior: getAttribute returns null → extractPlaywrightValue returns ""
            // buildRecords then sees all values are blank → skips the row
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("id").selector("div").type(FieldType.ATTRIBUTE)
                    .attribute("data-id").build();

            when(playwrightPage.querySelectorAll("div")).thenReturn(List.of(element));
            when(element.getAttribute("data-id")).thenReturn(null);

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            // Element matched but value was empty → row filtered by buildRecords
            assertThat(output.records()).isEmpty();
        }
    }

    // ── Selenium path ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Selenium path")
    class SeleniumPath {

        @Test
        @DisplayName("extracts TEXT using executeScript innerText")
        void extract_textType_usesExecuteScript() {
            var snapshot = snapshotWithSelenium();
            when(seleniumDriver.executeScript(anyString(), anyString()))
                    .thenReturn("Widget Name");

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", ".product-name"))));

            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst().get("name")).isEqualTo("Widget Name");
        }

        @Test
        @DisplayName("returns empty when all Selenium values are null")
        void extract_allNullValues_returnsEmpty() {
            var snapshot = snapshotWithSelenium();
            when(seleniumDriver.executeScript(anyString(), anyString())).thenReturn(null);

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", ".name"))));

            assertThat(output.records()).isEmpty();
        }

        @Test
        @DisplayName("strips multiline whitespace from Selenium string result")
        void extract_seleniumResult_stripsNewlines() {
            var snapshot = snapshotWithSelenium();
            when(seleniumDriver.executeScript(anyString(), anyString()))
                    .thenReturn("Widget\n  Name");

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", ".name"))));

            assertThat(output.records().getFirst().get("name")).isEqualTo("Widget Name");
        }
    }
}