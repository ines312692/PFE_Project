package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.FieldConfig;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.microsoft.playwright.Locator;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class XPathExtractor implements BaseExtractor {

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.XPATH;
    }

    @Override
    public ExtractionOutput extract(PageSnapshot snapshot, InternalScrapeRequest request) {
        if (request.getFields().isEmpty()) {
            log.warn("[XPATH] No fields defined");
            return ExtractionOutput.empty();
        }

        LiveBrowserContext ctx = snapshot.getBrowserContext();
        if (ctx == null) {
            log.error("[XPATH] No browser context in snapshot");
            return ExtractionOutput.empty();
        }

        if (ctx.isPlaywright()) return playwrightPath(ctx.getPage(), request.getFields());
        if (ctx.isSelenium()) return seleniumPath(ctx.getDriver(), request.getFields());

        log.error("[XPATH] BrowserContext has no live browser reference");
        return ExtractionOutput.empty();
    }

    private ExtractionOutput playwrightPath(
            com.microsoft.playwright.Page page, List<FieldConfig> fields) {

        Map<String, List<String>> fieldValues = new LinkedHashMap<>();
        int maxCount = 0;

        for (FieldConfig field : fields) {
            try {
                Locator locator = page.locator("xpath=" + field.getSelector());
                int count = locator.count();
                List<String> values = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    values.add(extractLocatorValue(locator.nth(i), field));
                }
                fieldValues.put(field.getName(), values);
                maxCount = Math.max(maxCount, values.size());
                log.debug("[XPATH/PW] '{}' → {} match(es)", field.getName(), values.size());
            } catch (Exception e) {
                log.error("[XPATH/PW] Field '{}' xpath='{}': {}",
                        field.getName(), field.getSelector(), e.getMessage());
                fieldValues.put(field.getName(), List.of());
            }
        }

        if (maxCount == 0) {
            log.warn("[XPATH/PW] No elements matched");
            return ExtractionOutput.empty();
        }
        return ExtractionOutput.of(ExtractionUtils.buildRecords(fields, fieldValues, maxCount));
    }

    private String extractLocatorValue(Locator loc, FieldConfig field) {
        try {
            return switch (field.getType()) {
                case ATTRIBUTE -> {
                    if (field.getAttribute() == null) yield "";
                    String v = loc.getAttribute(field.getAttribute());
                    yield v != null ? v.strip() : "";
                }
                case HTML -> loc.innerHTML();
                case IMAGE -> {
                    for (String attr : List.of("src", "data-src", "data-lazy-src")) {
                        String v = loc.getAttribute(attr);
                        if (v != null && !v.isBlank()) yield v.strip();
                    }
                    yield "";
                }
                case URL -> {
                    String v = loc.getAttribute("href");
                    yield v != null ? v.strip() : "";
                }
                case NUMBER -> {
                    String text = loc.innerText();
                    String normalized = text != null ? text.replace(",", ".") : "";
                    Matcher m = Pattern.compile("\\d+[.,]?\\d*").matcher(normalized);
                    String fallback = text != null ? text : "";
                    yield m.find() ? m.group() : fallback;
                }
                default -> {
                    String text = loc.innerText();
                    yield text != null ? text.strip() : "";
                }
            };
        } catch (Exception e) {
            log.debug("[XPATH/PW] Value extraction failed: {}", e.getMessage());
            return "";
        }
    }

    private ExtractionOutput seleniumPath(WebDriver driver, List<FieldConfig> fields) {
        Map<String, Object> row = new LinkedHashMap<>();

        for (FieldConfig field : fields) {
            try {
                List<WebElement> els = driver.findElements(By.xpath(field.getSelector()));
                if (els.isEmpty()) {
                    row.put(field.getName(), null);
                    continue;
                }
                String value = extractSeleniumValue(els.getFirst(), field);
                if (value != null) value = value.replaceAll("\\s*\\n\\s*", " ").strip();
                row.put(field.getName(), value != null && !value.isBlank() ? value : null);
            } catch (Exception e) {
                log.error("[XPATH/SE] Field '{}': {}", field.getName(), e.getMessage());
                row.put(field.getName(), null);
            }
        }

        boolean hasData = row.values().stream().anyMatch(Objects::nonNull);
        return hasData ? ExtractionOutput.of(List.of(row)) : ExtractionOutput.empty();
    }

    private String extractSeleniumValue(WebElement el, FieldConfig field) {
        return switch (field.getType()) {
            case ATTRIBUTE -> el.getAttribute(
                    field.getAttribute() != null ? field.getAttribute() : "");
            case IMAGE -> {
                String src = el.getAttribute("src");
                yield src != null && !src.isBlank()
                        ? src
                        : Objects.toString(el.getAttribute("data-src"), "");
            }
            case URL -> Optional.ofNullable(el.getAttribute("href")).orElse("");
            default -> el.getText();
        };
    }
}