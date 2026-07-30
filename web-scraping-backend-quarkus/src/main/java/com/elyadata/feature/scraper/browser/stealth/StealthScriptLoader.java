package com.elyadata.feature.scraper.browser.stealth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Slf4j
@ApplicationScoped
public class StealthScriptLoader {

    private static final String RESOURCE_ROOT = "stealth/playwright/";

    private static final List<String> SCRIPTS_BASIC = List.of(
            "01-webdriver.js",
            "02-chrome-runtime.js",
            "03-permissions.js"
    );

    private static final List<String> SCRIPTS_STANDARD_EXTRA = List.of(
            "04-plugins-mime.js",
            "05-webgl.js",
            "09-screen.js"
    );

    private static final List<String> SCRIPTS_AGGRESSIVE_EXTRA = List.of(
            "06-canvas.js",
            "07-audio.js",
            "08-navigator-hw.js",
            "10-webrtc.js"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, String> rawScripts;

    @PostConstruct
    void loadAll() {
        Map<String, String> loaded = new LinkedHashMap<>();
        for (String name : allScriptNames()) {
            loaded.put(name, readResource(RESOURCE_ROOT + name));
        }
        this.rawScripts = Map.copyOf(loaded);
        log.info("[STEALTH] Loaded {} stealth scripts from classpath", rawScripts.size());
    }

    public List<String> resolveScripts(StealthLevel level, ConsistencyProfile profile) {
        List<String> names = scriptsFor(level);
        return names.stream()
                .map(n -> substitute(rawScripts.get(n), profile))
                .toList();
    }

    private List<String> scriptsFor(StealthLevel level) {
        return switch (level) {
            case NONE -> List.of();
            case BASIC -> SCRIPTS_BASIC;
            case STANDARD -> concat(SCRIPTS_BASIC, SCRIPTS_STANDARD_EXTRA);
            case AGGRESSIVE -> concat(SCRIPTS_BASIC, SCRIPTS_STANDARD_EXTRA, SCRIPTS_AGGRESSIVE_EXTRA);
        };
    }

    private String substitute(String script, ConsistencyProfile p) {
        try {
            return script
                    .replace("{{WEBGL_VENDOR}}", p.webglVendor())
                    .replace("{{WEBGL_RENDERER}}", p.webglRenderer())
                    .replace("{{HW_CONCURRENCY}}", String.valueOf(p.hardwareConcurrency()))
                    .replace("{{DEVICE_MEMORY}}", String.valueOf(p.deviceMemory()))
                    .replace("{{PLATFORM}}", p.platform())
                    .replace("{{MAX_TOUCH_POINTS}}", String.valueOf(p.maxTouchPoints()))
                    .replace("{{VENDOR}}", p.vendor())
                    .replace("{{LANGUAGE}}", p.language())
                    .replace("{{LANGUAGES_JSON}}", objectMapper.writeValueAsString(p.languages()))
                    .replace("{{SCREEN_WIDTH}}", String.valueOf(p.screenWidth()))
                    .replace("{{SCREEN_HEIGHT}}", String.valueOf(p.screenHeight()))
                    .replace("{{SCREEN_AVAIL_WIDTH}}", String.valueOf(p.screenAvailWidth()))
                    .replace("{{SCREEN_AVAIL_HEIGHT}}", String.valueOf(p.screenAvailHeight()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize languages list", e);
        }
    }

    private static String readResource(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(path)) {
            Objects.requireNonNull(is, "Stealth script not found on classpath: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load stealth script: " + path, e);
        }
    }

    private static List<String> allScriptNames() {
        return concat(SCRIPTS_BASIC, SCRIPTS_STANDARD_EXTRA, SCRIPTS_AGGRESSIVE_EXTRA);
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
    }
}