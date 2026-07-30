package com.elyadata.feature.llmusage.application.controller;

import com.elyadata.common.llm.observability.LlmCallEntity;
import com.elyadata.common.llm.observability.LlmCallRepository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;


@Path("/v1/llm-usage")
@Produces(MediaType.APPLICATION_JSON)
public class LlmUsageController {


   private final LlmCallRepository repository;

    public LlmUsageController(LlmCallRepository repository) {
        this.repository = repository;
    }

    @GET
    @Path("/summary")
    public SummaryDto summary(@QueryParam("projectId") UUID projectId,
                              @QueryParam("from") String from,
                              @QueryParam("to") String to) {
        Instant f = parseFrom(from);
        Instant t = parseTo(to);
        Object[] r = repository.summary(projectId, f, t);
        long calls = lng(r[0]);
        long success = lng(r[6]);
        return new SummaryDto(
                calls,
                lng(r[1]), lng(r[2]), lng(r[3]),
                bd(r[4]),
                dbl(r[5]),
                calls == 0 ? 0.0 : round(success * 100.0 / calls),
                lng(r[7]));
    }

    @GET
    @Path("/by-feature")
    public List<FeatureDto> byFeature(@QueryParam("projectId") UUID projectId,
                                      @QueryParam("from") String from,
                                      @QueryParam("to") String to) {
        return repository.byFeature(projectId, parseFrom(from), parseTo(to)).stream()
                .map(r -> {
                    long calls = lng(r[1]);
                    long success = lng(r[4]);
                    return new FeatureDto(str(r[0]), calls, lng(r[2]), dbl(r[3]),
                            calls == 0 ? 0.0 : round(success * 100.0 / calls));
                })
                .toList();
    }

    @GET
    @Path("/by-model")
    public List<ModelDto> byModel(@QueryParam("projectId") UUID projectId,
                                  @QueryParam("from") String from,
                                  @QueryParam("to") String to) {
        return repository.byModel(projectId, parseFrom(from), parseTo(to)).stream()
                .map(r -> new ModelDto(str(r[0]), lng(r[1]), lng(r[2]), bd(r[3])))
                .toList();
    }

    @GET
    @Path("/by-project")
    public List<ProjectDto> byProject(@QueryParam("from") String from,
                                      @QueryParam("to") String to) {
        return repository.byProject(parseFrom(from), parseTo(to)).stream()
                .map(r -> new ProjectDto(uuid(r[0]), lng(r[1]), lng(r[2]), bd(r[3]), dbl(r[4])))
                .toList();
    }

    @GET
    @Path("/daily")
    public List<DailyDto> daily(@QueryParam("projectId") UUID projectId,
                                @QueryParam("from") String from,
                                @QueryParam("to") String to) {
        return repository.dailyTokens(projectId, parseFrom(from), parseTo(to)).stream()
                .map(r -> new DailyDto(inst(r[0]), lng(r[1]), lng(r[2]), lng(r[3])))
                .toList();
    }

    @GET
    @Path("/recent")
    public List<CallDto> recent(@QueryParam("projectId") UUID projectId,
                                @QueryParam("limit") Integer limit) {
        int n = limit == null ? 20 : Math.min(Math.max(limit, 1), 200);
        return repository.recent(projectId, n).stream()
                .map(LlmUsageController::toDto)
                .toList();
    }

    public record SummaryDto(long calls, long promptTokens, long completionTokens, long totalTokens,
                             BigDecimal estimatedCost, double avgLatencyMs, double successRate,
                             long rateLimited) {}

    public record FeatureDto(String feature, long calls, long totalTokens,
                             double avgLatencyMs, double successRate) {}

    public record ModelDto(String model, long calls, long totalTokens, BigDecimal estimatedCost) {}

    public record ProjectDto(UUID projectId, long calls, long totalTokens,
                             BigDecimal estimatedCost, double avgLatencyMs) {}

    public record DailyDto(Instant day, long calls, long totalTokens, long failed) {}

    public record CallDto(UUID id, Instant createdAt, String provider, String model, String feature,
                          String outcome, UUID jobId, UUID projectId, Integer promptTokens,
                          Integer completionTokens, Integer totalTokens, int latencyMs,
                          BigDecimal estimatedCost, String errorMessage) {}

    private static CallDto toDto(LlmCallEntity e) {
        return new CallDto(e.getId(), e.getCreatedAt(), e.getProvider(), e.getModel(), e.getFeature(),
                e.getOutcome(), e.getJobId(), e.getProjectId(), e.getPromptTokens(),
                e.getCompletionTokens(), e.getTotalTokens(), e.getLatencyMs(),
                e.getEstimatedCost(), e.getErrorMessage());
    }


    private static Instant parseFrom(String s) {
        return s == null || s.isBlank() ? Instant.now().minus(30, ChronoUnit.DAYS) : Instant.parse(s);
    }

    private static Instant parseTo(String s) {
        return s == null || s.isBlank() ? Instant.now() : Instant.parse(s);
    }

    private static long lng(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static double dbl(Object o) {
        return o == null ? 0.0 : round(((Number) o).doubleValue());
    }

    private static BigDecimal bd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) o).doubleValue());
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static UUID uuid(Object o) {
        return o == null ? null : (o instanceof UUID u ? u : UUID.fromString(o.toString()));
    }

    private static Instant inst(Object o) {
        return switch (o) {
            case null -> null;
            case Instant i -> i;
            case java.sql.Timestamp ts -> ts.toInstant();
            case java.time.OffsetDateTime odt -> odt.toInstant();
            default -> Instant.parse(o.toString());
        };
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
