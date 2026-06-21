package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.*;

@Slf4j
@ApplicationScoped
public class RegexExtractor implements BaseExtractor {

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.REGEX;
    }

    @Override
    public ExtractionOutput extract(PageSnapshot snapshot, InternalScrapeRequest request) {
        String pattern = request.getRegexPattern();
        String groupName = request.getRegexGroup();

        if (pattern == null || pattern.isBlank()) {
            log.error("[REGEX] No pattern provided");
            return ExtractionOutput.empty();
        }

        String content = snapshot.getText() != null && !snapshot.getText().isBlank()
                ? snapshot.getText()
                : snapshot.getHtml();

        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern, Pattern.DOTALL | Pattern.MULTILINE);
        } catch (PatternSyntaxException e) {
            log.error("[REGEX] Invalid pattern '{}': {}", pattern, e.getMessage());
            return ExtractionOutput.empty();
        }

        List<MatchResult> matches = compiled.matcher(content).results().toList();
        log.info("[REGEX] Pattern found {} match(es) on {}", matches.size(), snapshot.getUrl());

        if (matches.isEmpty()) {
            log.warn("[REGEX] No matches");
            return ExtractionOutput.empty();
        }

        List<String> namedGroups = namedGroupsOf(compiled);
        List<Map<String, Object>> records = new ArrayList<>();

        for (MatchResult m : matches) {
            Map<String, Object> entry = matchToRecord(m, groupName, compiled, namedGroups);
            if (entry != null) records.add(entry);
        }

        log.info("[REGEX] {} record(s) built", records.size());
        return ExtractionOutput.of(records);
    }

    private Map<String, Object> matchToRecord(
            MatchResult m, String explicitGroup, Pattern compiled, List<String> namedGroups) {

        Matcher matcher = compiled.matcher(m.group(0));
        if (!matcher.find()) return Collections.emptyMap();

        if (!namedGroups.isEmpty()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String name : namedGroups) {
                String val = matcher.group(name);
                if (val != null) entry.put(name, val);
            }
            return entry.isEmpty() ? null : entry;
        }

        if (explicitGroup != null) {
            try {
                String val = matcher.group(explicitGroup);
                return val != null ? Map.of(explicitGroup, val.strip()) : null;
            } catch (IllegalArgumentException e) {
                log.debug("[REGEX] Named group '{}' not found in pattern — using full match", explicitGroup);
            }
        }

        String full = m.group(0).strip();
        return full.isBlank() ? null : Map.of("match", full);
    }

    private static List<String> namedGroupsOf(Pattern pattern) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>")
                .matcher(pattern.pattern());
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}