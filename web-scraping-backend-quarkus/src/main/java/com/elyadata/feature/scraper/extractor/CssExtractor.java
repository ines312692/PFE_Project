package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.LiveBrowserContext;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.FieldConfig;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.microsoft.playwright.ElementHandle;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class CssExtractor implements BaseExtractor {

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.CSS_SELECTOR;
    }

    @Override
    public ExtractionOutput extract(PageSnapshot snapshot, InternalScrapeRequest request) {
        if (request.getFields().isEmpty()) {
            log.warn("[CSS] No fields defined");
            return ExtractionOutput.empty();
        }

        LiveBrowserContext ctx = snapshot.getBrowserContext();
        if (ctx == null) {
            log.error("[CSS] No browser context in snapshot");
            return ExtractionOutput.empty();
        }

        if (ctx.isPlaywright()) return playwrightPath(ctx.getPage(), request.getFields());
        if (ctx.isSelenium()) return seleniumPath(ctx.getDriver(), request.getFields());

        log.error("[CSS] BrowserContext has no live browser reference");
        return ExtractionOutput.empty();
    }

    private ExtractionOutput playwrightPath(
            com.microsoft.playwright.Page page, List<FieldConfig> fields) {

        Map<String, List<String>> fieldValues = new LinkedHashMap<>();
        int maxCount = 0;

        for (FieldConfig field : fields) {
            try {
                List<ElementHandle> els = page.querySelectorAll(field.getSelector());
                List<String> values = els.stream()
                        .map(el -> extractPlaywrightValue(el, field))
                        .toList();
                fieldValues.put(field.getName(), values);
                maxCount = Math.max(maxCount, values.size());
                log.debug("[CSS/PW] '{}' → {} match(es)", field.getName(), values.size());
            } catch (Exception e) {
                log.error("[CSS/PW] Field '{}' selector='{}': {}",
                        field.getName(), field.getSelector(), e.getMessage());
                fieldValues.put(field.getName(), List.of());
            }
        }

        if (maxCount == 0) {
            log.warn("[CSS/PW] No elements matched");
            return ExtractionOutput.empty();
        }
        return ExtractionOutput.of(ExtractionUtils.buildRecords(fields, fieldValues, maxCount));
    }

    private String extractPlaywrightValue(ElementHandle el, FieldConfig field) {
        try {
            return switch (field.getType()) {
                case ATTRIBUTE -> {
                    if (field.getAttribute() == null) yield "";
                    String v = el.getAttribute(field.getAttribute());
                    yield v != null ? v.strip() : "";
                }
                case HTML -> el.innerHTML();
                case IMAGE -> {
                    for (String attr : List.of("src", "data-src", "data-lazy-src")) {
                        String v = el.getAttribute(attr);
                        if (v != null && !v.isBlank()) yield v.strip();
                    }
                    yield "";
                }
                case URL -> {
                    String v = el.getAttribute("href");
                    yield v != null ? v.strip() : "";
                }
                case NUMBER -> {
                    String text = el.innerText();
                    String input = text != null ? text.replace(",", ".") : "";
                    Matcher matcher = Pattern.compile("[\\d]+[.,]?\\d*").matcher(input);
                    if (matcher.find()) {
                        yield matcher.group();
                    }
                    yield text != null ? text : "";
                }
                default -> {
                    String text = el.innerText();
                    yield text != null ? text.strip() : "";
                }
            };
        } catch (Exception e) {
            log.error("[CSS/PW] Value extraction for '{}': {}", field.getName(), e.getMessage());
            return "";
        }
    }

    private ExtractionOutput seleniumPath(WebDriver driver, List<FieldConfig> fields) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Map<String, Object> entry = new LinkedHashMap<>();

        for (FieldConfig field : fields) {
            try {
                Object value = switch (field.getType()) {
                    case ATTRIBUTE -> js.executeScript(
                            "var e = document.querySelector(arguments[0]);" +
                                    "return e ? e.getAttribute(arguments[1]) : '';",
                            field.getSelector(),
                            field.getAttribute() != null ? field.getAttribute() : ""
                    );
                    case IMAGE -> js.executeScript(
                            "var e = document.querySelector(arguments[0]);" +
                                    "return e ? (e.src || e.getAttribute('data-src') || '') : '';",
                            field.getSelector()
                    );
                    case URL -> js.executeScript(
                            "var e = document.querySelector(arguments[0]);" +
                                    "return e ? (e.href || '') : '';",
                            field.getSelector()
                    );
                    default -> js.executeScript(
                            "var e = document.querySelector(arguments[0]);" +
                                    "return e ? e.innerText.trim() : '';",
                            field.getSelector()
                    );
                };

                if (value instanceof String s) {
                    value = s.replaceAll("\\s*\\n\\s*", " ").strip();
                }
                entry.put(field.getName(),
                        value != null && !value.toString().isBlank() ? value : null);

                log.debug("[CSS/SE] '{}' → '{}'", field.getName(),
                        value != null
                                ? value.toString().substring(0, Math.min(80, value.toString().length()))
                                : "null");

            } catch (Exception e) {
                log.error("[CSS/SE] Field '{}': {}", field.getName(), e.getMessage());
                entry.put(field.getName(), null);
            }
        }

        boolean hasData = entry.values().stream().anyMatch(Objects::nonNull);
        return hasData ? ExtractionOutput.of(List.of(entry)) : ExtractionOutput.empty();
    }
}