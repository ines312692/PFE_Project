package com.elyadata.feature.dataversion.domain.service;

import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.port.DataVersionPort;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DataVersionServiceImpl implements DataVersionService {

    private final DataVersionPort dataVersionPort;
    private final ScrapeResultPort scrapeResultPort;

    @Override
    @Transactional
    public DataVersion createVersion(NewVersion command) {
        ScrapeResult raw = requireScrapeResult(command.scrapeResultId());

        DataVersion latest = dataVersionPort.findLatest(command.scrapeResultId()).orElse(null);
        int nextNumber = latest != null ? latest.versionNumber() + 1 : 1;
        UUID parentId = latest != null ? latest.id() : null;

        List<Map<String, Object>> records =
                command.records() != null ? command.records() : List.of();
        List<Map<String, Object>> steps =
                command.appliedSteps() != null ? command.appliedSteps() : List.of();

        DataVersion toSave = DataVersion.builder()
                .scrapeResultId(raw.id())
                .versionNumber(nextNumber)
                .parentVersionId(parentId)
                .records(records)
                .appliedSteps(steps)
                .recordCount(records.size())
                .label(command.label() != null ? command.label() : defaultLabel(steps))
                .createdBy(command.createdBy())
                .createdAt(Instant.now())
                .build();

        DataVersion saved = dataVersionPort.save(toSave);
        log.info("[DATA-VERSION] Created v{} for scrape-result={} ({} records)",
                saved.versionNumber(), saved.scrapeResultId(), saved.recordCount());
        return saved;
    }

    @Override
    public List<DataVersion> listVersions(UUID scrapeResultId) {
        ScrapeResult raw = requireScrapeResult(scrapeResultId);
        List<DataVersion> history = new ArrayList<>();
        history.add(rawVersionOf(raw));
        history.addAll(dataVersionPort.findByScrapeResultId(scrapeResultId));
        return history;
    }

    @Override
    public DataVersion getVersion(UUID scrapeResultId, int versionNumber) {
        if (versionNumber == DataVersion.RAW_VERSION) {
            return rawVersionOf(requireScrapeResult(scrapeResultId));
        }
        return dataVersionPort.findByScrapeResultIdAndVersionNumber(scrapeResultId, versionNumber)
                .orElseThrow(() -> new NotFoundException(
                        "Version " + versionNumber + " not found for scrape-result " + scrapeResultId));
    }

    @Override
    @Transactional
    public DataVersion rollback(UUID scrapeResultId, int targetVersionNumber, UUID userId) {
        DataVersion target = getVersion(scrapeResultId, targetVersionNumber);
        log.info("[DATA-VERSION] Rolling back scrape-result={} to v{}",
                scrapeResultId, targetVersionNumber);
        return createVersion(new NewVersion(
                scrapeResultId,
                target.records(),
                target.appliedSteps(),
                "Rollback to v" + targetVersionNumber,
                userId));
    }


    private ScrapeResult requireScrapeResult(UUID scrapeResultId) {
        return scrapeResultPort.findById(scrapeResultId)
                .orElseThrow(() -> new NotFoundException(
                        "Scrape result not found: " + scrapeResultId));
    }

    private static DataVersion rawVersionOf(ScrapeResult raw) {
        List<Map<String, Object>> records =
                raw.records() != null ? raw.records() : List.of();
        return DataVersion.builder()
                .id(null)
                .scrapeResultId(raw.id())
                .versionNumber(DataVersion.RAW_VERSION)
                .parentVersionId(null)
                .records(records)
                .appliedSteps(List.of())
                .recordCount(records.size())
                .label("Raw scrape result")
                .createdBy(null)
                .createdAt(raw.scrapedAt())
                .build();
    }

    private static String defaultLabel(List<Map<String, Object>> appliedSteps) {
        int count = appliedSteps.size();
        return "ETL run  " + count + " step" + (count == 1 ? "" : "s");
    }
}
