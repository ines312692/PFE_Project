package com.elyadata.feature.etl;

import com.elyadata.feature.etl.domain.model.EtlConfig;
import com.elyadata.feature.etl.domain.model.EtlStepConfig;
import com.elyadata.feature.etl.domain.model.QualityReport;
import com.elyadata.feature.etl.step.EtlStep;
import com.elyadata.feature.etl.step.EtlStepRegistry;
import com.elyadata.feature.etl.step.ValidateFieldsStep;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class EtlPipelineRunner {

    private final EtlStepRegistry registry;

    @Inject
    public EtlPipelineRunner(EtlStepRegistry registry) {
        this.registry = registry;
    }

    public EtlRunResult run(List<Map<String, Object>> rawRecords, EtlConfig config) {
        if (!config.hasSteps()) {
            ValidateFieldsStep.drainReport();
            return EtlRunResult.noOp(rawRecords);
        }

        long start = System.currentTimeMillis();
        List<Map<String, Object>> current = List.copyOf(rawRecords);
        List<StepReport> reports = new ArrayList<>();

        for (EtlStepConfig stepConfig : config.steps()) {
            int before = current.size();
            try {
                EtlStep step = registry.get(stepConfig.type());
                current = step.apply(current,
                        stepConfig.params() != null ? stepConfig.params() : Map.of());
                reports.add(StepReport.ok(stepConfig.type().name(), before, current.size()));
                log.info("[ETL] Step {} — {} → {} records",
                        stepConfig.type(), before, current.size());
            } catch (Exception e) {
                log.error("[ETL] Step {} failed: {}", stepConfig.type(), e.getMessage());
                reports.add(StepReport.error(stepConfig.type().name(), before, e.getMessage()));
            }
        }

        // Récupère le rapport produit par un éventuel step VALIDATE_FIELDS.
        // null si aucun step de validation n'a tourné.
        QualityReport qualityReport = ValidateFieldsStep.drainReport();

        long durationMs = System.currentTimeMillis() - start;
        int removed = rawRecords.size() - current.size();

        log.info("[ETL] Pipeline done — {}/{} records kept, {}ms",
                current.size(), rawRecords.size(), durationMs);

        return new EtlRunResult(current, reports, rawRecords.size(),
                current.size(), removed, durationMs, qualityReport);
    }

    public record StepReport(String step, int inputCount, int outputCount,
                             boolean success, String error) {
        static StepReport ok(String step, int in, int out) {
            return new StepReport(step, in, out, true, null);
        }

        static StepReport error(String step, int in, String msg) {
            return new StepReport(step, in, in, false, msg);
        }
    }

    public record EtlRunResult(
            List<Map<String, Object>> records,
            List<StepReport> stepReports,
            int rawCount,
            int cleanCount,
            int removedCount,
            long durationMs,
            QualityReport qualityReport) {

        static EtlRunResult noOp(List<Map<String, Object>> records) {
            return new EtlRunResult(records, List.of(),
                    records.size(), records.size(), 0, 0L, null);
        }
    }
}