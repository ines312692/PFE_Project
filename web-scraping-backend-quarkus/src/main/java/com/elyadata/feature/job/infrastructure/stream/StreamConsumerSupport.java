package com.elyadata.feature.job.infrastructure.stream;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.XGroupCreateArgs;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class StreamConsumerSupport {

    private static final String BUSYGROUP = "BUSYGROUP";

    private final StreamCommands<String, String, String> streams;
    private final HashCommands<String, String, Long> attempts;

    public StreamConsumerSupport(RedisDataSource redisDataSource) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
        this.attempts = redisDataSource.hash(Long.class);
    }

    private static String attemptsKey(String stream) {
        return RedisStreamNames.ATTEMPTS_KEY_PREFIX + stream;
    }

    public long recordAttempt(String stream, String messageId) {
        return attempts.hincrby(attemptsKey(stream), messageId, 1L);
    }

    public void ack(String stream, String group, String messageId) {
        try {
            streams.xack(stream, group, messageId);
            attempts.hdel(attemptsKey(stream), messageId);
        } catch (Exception e) {
            log.warn("[STREAM] XACK failed stream={} group={} id={}: {}", stream, group, messageId, e.getMessage());
        }
    }

    public void handleFailure(String stream, String group, String dlqStream,
                              String messageId, Map<String, String> payload,
                              long deliveryCount, int maxDeliveries, String reason) {
        if (deliveryCount >= maxDeliveries) {
            toDeadLetter(dlqStream, messageId, payload, reason);
            ack(stream, group, messageId);
            log.error("[STREAM] Message id={} → DLQ après {} livraison(s) (raison: {})", messageId, deliveryCount, reason);
            return;
        }
        log.warn("[STREAM] Message id={} en échec (livraison {}/{}) — laissé en PEL pour retry (raison: {})",
                messageId, deliveryCount, maxDeliveries, reason);
    }

    private void toDeadLetter(String dlqStream, String originId, Map<String, String> originPayload, String reason) {
        Map<String, String> dlq = new LinkedHashMap<>(originPayload);
        dlq.put(RedisStreamNames.FIELD_DLQ_REASON, truncate(reason));
        dlq.put(RedisStreamNames.FIELD_ORIGIN_ID, originId);
        try {
            streams.xadd(dlqStream, dlq);
        } catch (Exception e) {
            log.error("[STREAM] Échec push id={} vers DLQ {}: {}", originId, dlqStream, e.getMessage());
        }
    }

    public void ensureGroup(String stream, String group) {
        try {
            streams.xgroupCreate(stream, group, "$", new XGroupCreateArgs().mkstream());
            log.info("[STREAM] Groupe '{}' créé sur '{}'", group, stream);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains(BUSYGROUP)) return;
            log.warn("[STREAM] ensureGroup échec '{}' sur '{}': {}", group, stream, msg);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "unknown";
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}