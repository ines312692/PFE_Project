package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.FieldConfig;
import com.elyadata.feature.scraper.model.FieldType;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.microsoft.playwright.Locator;
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
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("XPathExtractor")
class XPathExtractorTest {

    private XPathExtractor extractor;

    @Mock private Page playwrightPage;
    @Mock private Locator locator;
    @Mock private LiveBrowserContext browserContext;
    @Mock private WebElement webElement;

    interface SeleniumDriver extends WebDriver, JavascriptExecutor {}
    @Mock private SeleniumDriver seleniumDriver;

    @BeforeEach
    void setUp() {
        extractor = new XPathExtractor();
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

    private static FieldConfig textField(String name, String xpath) {
        return FieldConfig.builder().name(name).selector(xpath)
                .type(FieldType.TEXT).build();
    }

    private static InternalScrapeRequest requestWithFields(List<FieldConfig> fields) {
        return InternalScrapeRequest.builder()
                .targetUrl("https://example.com")
                .fields(fields)
                .build();
    }

    @Test
    @DisplayName("mode() returns XPATH")
    void mode_returnsXPath() {
        assertThat(extractor.mode()).isEqualTo(ExtractionMode.XPATH);
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
            var request = requestWithFields(List.of(textField("name", "//h1")));

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }

        @Test
        @DisplayName("returns empty when no Playwright elements matched")
        void extract_playwright_noElementsMatched_returnsEmpty() {
            var snapshot = snapshotWithPlaywright();
            when(playwrightPage.locator(anyString())).thenReturn(locator);
            when(locator.count()).thenReturn(0);
            var request = requestWithFields(List.of(textField("name", "//h1")));

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }
    }
    @Nested
    @DisplayName("Playwright path")
    class PlaywrightPath {

        @Test
        @DisplayName("calls page.locator with 'xpath=' prefix")
        void extract_usesXpathPrefixOnLocator() {
            var snapshot = snapshotWithPlaywright();
            when(playwrightPage.locator("xpath=//h1")).thenReturn(locator);
            when(locator.count()).thenReturn(1);
            Locator nth = mock(Locator.class);
            when(locator.nth(0)).thenReturn(nth);
            when(nth.innerText()).thenReturn("Title");

            extractor.extract(snapshot, requestWithFields(List.of(textField("title", "//h1"))));

            verify(playwrightPage).locator("xpath=//h1");
        }

        @Test
        @DisplayName("extracts TEXT type using innerText()")
        void extract_textType_usesInnerText() {
            var snapshot = snapshotWithPlaywright();
            when(playwrightPage.locator(anyString())).thenReturn(locator);
            when(locator.count()).thenReturn(1);
            Locator nth = mock(Locator.class);
            when(locator.nth(0)).thenReturn(nth);
            when(nth.innerText()).thenReturn("  Product Title  ");

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("title", "//h1"))));

            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst().get("title")).isEqualTo("Product Title");
        }

        @Test
        @DisplayName("extracts ATTRIBUTE type using getAttribute()")
        void extract_attributeType_usesGetAttribute() {
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("link").selector("//a[@class='item']")
                    .type(FieldType.ATTRIBUTE).attribute("href").build();

            when(playwrightPage.locator(anyString())).thenReturn(locator);
            when(locator.count()).thenReturn(1);
            Locator nth = mock(Locator.class);
            when(locator.nth(0)).thenReturn(nth);
            when(nth.getAttribute("href")).thenReturn("https://target.com");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("link")).isEqualTo("https://target.com");
        }

        @Test
        @DisplayName("extracts URL type from href attribute")
        void extract_urlType_usesHrefAttribute() {
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("url").selector("//a").type(FieldType.URL).build();

            when(playwrightPage.locator(anyString())).thenReturn(locator);
            when(locator.count()).thenReturn(1);
            Locator nth = mock(Locator.class);
            when(locator.nth(0)).thenReturn(nth);
            when(nth.getAttribute("href")).thenReturn("https://example.com/page");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("url")).isEqualTo("https://example.com/page");
        }

        @Test
        @DisplayName("extracts IMAGE type from src attribute")
        void extract_imageType_usesSrcAttribute() {
            var snapshot = snapshotWithPlaywright();
            var field = FieldConfig.builder()
                    .name("img").selector("//img").type(FieldType.IMAGE).build();

            when(playwrightPage.locator(anyString())).thenReturn(locator);
            when(locator.count()).thenReturn(1);
            Locator nth = mock(Locator.class);
            when(locator.nth(0)).thenReturn(nth);
            when(nth.getAttribute("src")).thenReturn("https://cdn.example.com/image.jpg");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("img"))
                    .isEqualTo("https://cdn.example.com/image.jpg");
        }

        @Test
        @DisplayName("builds one record per matched element")
        void extract_multipleElements_buildsMultipleRecords() {
            var snapshot = snapshotWithPlaywright();
            when(playwrightPage.locator(anyString())).thenReturn(locator);
            when(locator.count()).thenReturn(2);
            Locator nth0 = mock(Locator.class);
            Locator nth1 = mock(Locator.class);
            when(locator.nth(0)).thenReturn(nth0);
            when(locator.nth(1)).thenReturn(nth1);
            when(nth0.innerText()).thenReturn("Alice");
            when(nth1.innerText()).thenReturn("Bob");

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", "//li"))));

            assertThat(output.records()).hasSize(2);
            assertThat(output.records().get(0).get("name")).isEqualTo("Alice");
            assertThat(output.records().get(1).get("name")).isEqualTo("Bob");
        }
    }

    @Nested
    @DisplayName("Selenium path")
    class SeleniumPath {

        @Test
        @DisplayName("uses By.xpath() to find elements")
        void extract_usesByXpath() {
            var snapshot = snapshotWithSelenium();
            when(seleniumDriver.findElements(By.xpath("//h1"))).thenReturn(List.of(webElement));
            when(webElement.getText()).thenReturn("Product Title");

            extractor.extract(snapshot, requestWithFields(List.of(textField("title", "//h1"))));

            verify(seleniumDriver).findElements(By.xpath("//h1"));
        }

        @Test
        @DisplayName("extracts TEXT using element.getText()")
        void extract_textType_usesGetText() {
            var snapshot = snapshotWithSelenium();
            when(seleniumDriver.findElements(any())).thenReturn(List.of(webElement));
            when(webElement.getText()).thenReturn("Product Name");

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", "//h1"))));

            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst().get("name")).isEqualTo("Product Name");
        }

        @Test
        @DisplayName("extracts ATTRIBUTE type using element.getAttribute()")
        void extract_attributeType_usesGetAttribute() {
            var snapshot = snapshotWithSelenium();
            var field = FieldConfig.builder()
                    .name("id").selector("//div").type(FieldType.ATTRIBUTE)
                    .attribute("data-id").build();

            when(seleniumDriver.findElements(any())).thenReturn(List.of(webElement));
            when(webElement.getAttribute("data-id")).thenReturn("item-42");

            var output = extractor.extract(snapshot, requestWithFields(List.of(field)));

            assertThat(output.records().getFirst().get("id")).isEqualTo("item-42");
        }

        @Test
        @DisplayName("returns empty when no elements found")
        void extract_noElements_returnsEmpty() {
            var snapshot = snapshotWithSelenium();
            when(seleniumDriver.findElements(any())).thenReturn(List.of());

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", "//h1"))));

            assertThat(output.records()).isEmpty();
        }

        @Test
        @DisplayName("strips newlines from Selenium text result")
        void extract_seleniumText_stripsNewlines() {
            var snapshot = snapshotWithSelenium();
            when(seleniumDriver.findElements(any())).thenReturn(List.of(webElement));
            when(webElement.getText()).thenReturn("Product\n  Name");

            var output = extractor.extract(snapshot, requestWithFields(List.of(textField("name", "//h1"))));

            assertThat(output.records().getFirst().get("name")).isEqualTo("Product Name");
        }
    }
}