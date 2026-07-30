package com.elyadata.feature.etl.infrastructure.adapter;

import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.port.DataVersionPort;
import com.elyadata.feature.etl.EtlPipelineRunner;
import com.elyadata.feature.etl.EtlPipelineRunner.EtlRunResult;
import com.elyadata.feature.etl.domain.model.EtlConfig;
import com.elyadata.feature.etl.domain.service.port.EtlConfigPort;
import com.elyadata.feature.job.domain.service.port.EtlRunPort;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class EtlRunAdapter implements EtlRunPort {

    private final EtlPipelineRunner runner;
    private final ScrapeResultPort scrapeResultPort;
    private final DataVersionPort dataVersionPort;
    private final EtlConfigPort etlConfigPort;

    @Override
    @Transactional
    public void runEtlSync(UUID jobId) {
        // 1. Charger le résultat de scrape (source des records bruts).
        Optional<ScrapeResult> maybeResult = scrapeResultPort.findByJobId(jobId);
        if (maybeResult.isEmpty()) {
            log.warn("[ETL] Aucun ScrapeResult pour job={} — skip", jobId);
            return;
        }
        ScrapeResult scrapeResult = maybeResult.get();

        // 2. Config ETL du job. Aucune step configurée → rien à faire.
        EtlConfig config = etlConfigPort.findByJobId(jobId).orElse(EtlConfig.empty());
        if (!config.hasSteps()) {
            log.info("[ETL] Aucune step ETL configurée pour job={} — skip", jobId);
            return;
        }

        // 3. Idempotence : si une DataVersion ETL (non-raw) existe déjà → skip.
        Optional<DataVersion> latest = dataVersionPort.findLatest(scrapeResult.id());
        if (latest.isPresent() && !latest.get().isRaw()) {
            log.info("[ETL] DataVersion ETL déjà présente pour scrapeResult={} — skip (idempotent)",
                    scrapeResult.id());
            return;
        }

        // 4. Exécuter le pipeline (synchrone, dans CE thread worker).
        List<Map<String, Object>> rawRecords =
                scrapeResult.records() != null ? scrapeResult.records() : List.of();
        EtlRunResult result = runner.run(rawRecords, config);

        log.info("[ETL] job={} — {}/{} records conservés, {} step(s), {}ms",
                jobId, result.cleanCount(), result.rawCount(),
                result.stepReports().size(), result.durationMs());

        // 5. Persister comme nouvelle DataVersion (la version raw, si présente, devient le parent).
        UUID parentId = latest.map(DataVersion::id).orElse(null);
        int versionNumber = latest.map(v -> v.versionNumber() + 1).orElse(1);

        dataVersionPort.save(DataVersion.builder()
                .scrapeResultId(scrapeResult.id())
                .versionNumber(versionNumber)
                .parentVersionId(parentId)
                .records(result.records())
                .appliedSteps(toAppliedSteps(result))
                .recordCount(result.cleanCount())
                .label("ETL v" + versionNumber)
                .createdBy(null)
                .createdAt(Instant.now())
                .build());

        log.info("[ETL] DataVersion v{} enregistrée pour job={}", versionNumber, jobId);
    }

    private static List<Map<String, Object>> toAppliedSteps(EtlRunResult result) {
        return result.stepReports().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("step", r.step());
                    m.put("inputCount", r.inputCount());
                    m.put("outputCount", r.outputCount());
                    m.put("success", r.success());
                    m.put("error", r.error());
                    return m;
                })
                .toList();
    }
}