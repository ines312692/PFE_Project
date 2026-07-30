package com.elyadata.feature.scraper.crawler.frontier;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.stream.ClaimedMessages;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.quarkus.redis.datasource.stream.XGroupCreateArgs;
import io.quarkus.redis.datasource.stream.XReadGroupArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class DistributedCrawlFrontier {

    public static final String GROUP = "crawlers";
    private static final String F_URL = "url";
    private static final String F_DEPTH = "depth";
    private static final String F_PARENT = "parent";
    private static final Duration TTL = Duration.ofHours(6);

    private final StreamCommands<String, String, String> streams;
    private final SetCommands<String, String> sets;
    private final ValueCommands<String, Long> counters;
    private final HashCommands<String, String, String> hashes;
    private final HashCommands<String, String, Long> attemptHashes;
    private final KeyCommands<String> keys;

    public DistributedCrawlFrontier(RedisDataSource redis) {
        this.streams = redis.stream(String.class, String.class, String.class);
        this.sets = redis.set(String.class);
        this.counters = redis.value(Long.class);
        this.hashes = redis.hash(String.class);
        this.attemptHashes = redis.hash(Long.class);
        this.keys = redis.key();
    }

    private static String ready(UUID j) {
        return "crawl:" + j + ":ready";
    }

    private static String seen(UUID j) {
        return "crawl:" + j + ":seen";
    }

    private static String done(UUID j) {
        return "crawl:" + j + ":done";
    }

    private static String pages(UUID j) {
        return "crawl:" + j + ":pages";
    }

    private static String records(UUID j) {
        return "crawl:" + j + ":records";
    }

    private static String finalized(UUID j) {
        return "crawl:" + j + ":finalized";
    }

    private static String attempts(UUID j) {
        return "crawl:" + j + ":attempts";
    }

    public void ensureGroup(UUID jobId) {
        try {
            streams.xgroupCreate(ready(jobId), GROUP, "0", new XGroupCreateArgs().mkstream());
        } catch (Exception e) {
            if (!String.valueOf(e.getMessage()).contains("BUSYGROUP")) {
                log.warn("[FRONTIER] ensureGroup job={} : {}", jobId, e.getMessage());
            }
        }
    }

    public boolean frontierExists(UUID jobId) {
        try {
            return keys.exists(ready(jobId));
        } catch (Exception e) {
            log.warn("[FRONTIER] frontierExists job={} : {}", jobId, e.getMessage());
            return false;
        }
    }

    public void seed(UUID jobId, CrawlNode node) {
        enqueueIfNew(jobId, node);
    }

    public boolean expand(UUID jobId, CrawlNode child, int maxDepth) {
        if (child.depth() > maxDepth) return false;
        if (!keys.exists(ready(jobId))) {
            log.debug("[FRONTIER] expand job={} — frontier nettoyée, expansion ignorée (pas de résurrection)", jobId);
            return false;
        }
        return enqueueIfNew(jobId, child);
    }

    private boolean enqueueIfNew(UUID jobId, CrawlNode node) {
        long added = sets.sadd(seen(jobId), node.url());
        if (added == 0) return false;
        streams.xadd(ready(jobId), payload(node));
        keys.expire(ready(jobId), TTL);
        keys.expire(seen(jobId), TTL);
        return true;
    }

    public List<FrontierEntry> claim(UUID jobId, String consumer, int count) {
        try {
            return toEntries(streams.xreadgroup(GROUP, consumer, ready(jobId), ">",
                    new XReadGroupArgs().count(count)));
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("NOGROUP")) {
                log.debug("[FRONTIER] claim job={} — groupe absent (crawl finalisé)", jobId);
                return List.of();
            }
            throw e;
        }
    }

    public List<FrontierEntry> reclaim(UUID jobId, String consumer, Duration minIdle, int count) {
        // Ne PAS ressusciter un stream déjà nettoyé par le finalizer : sur certaines
        // versions de Redis, XAUTOCLAIM sur un stream absent le recrée vide, ce qui
        // fait croire à frontierExists() que la frontier est encore là et fait boucler
        // indéfiniment le renfort (drain RENFORT qui ne quitte jamais). On vérifie donc
        // d'abord l'existence de la clé et on renonce si elle a disparu.
        if (!keys.exists(ready(jobId))) {
            log.debug("[FRONTIER] reclaim job={} — frontier absente (nettoyée), rien à reclaim", jobId);
            return List.of();
        }
        try {
            ClaimedMessages<String, String, String> claimed =
                    streams.xautoclaim(ready(jobId), GROUP, consumer, minIdle, "0-0", count);
            return toEntries(claimed.getMessages());
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("NOGROUP")) {
                log.info("[FRONTIER] reclaim job={} — groupe absent (crawl finalisé), rien à reclaim", jobId);
                return List.of();
            }
            throw e;
        }
    }

    public long incrementPages(UUID jobId) {
        long n = counters.incr(pages(jobId));
        keys.expire(pages(jobId), TTL);
        return n;
    }

    public boolean underPageCap(UUID jobId, int maxPages) {
        Long n = counters.get(pages(jobId));
        return (n == null ? 0 : n) < maxPages;
    }

    public long pagesCount(UUID jobId) {
        Long n = counters.get(pages(jobId));
        return n == null ? 0 : n;
    }

    /**
     * Increments and returns the number of times {@code url} has been attempted-then-skipped
     * for this job (used to bound circuit-breaker-open retries). The counter shares the
     * frontier TTL and is wiped by {@link #cleanup(UUID)}.
     */
    public long recordUrlAttempt(UUID jobId, String url) {
        long n = attemptHashes.hincrby(attempts(jobId), url, 1L);
        keys.expire(attempts(jobId), TTL);
        return n;
    }

    public boolean complete(UUID jobId, String url, String messageId) {
        sets.sadd(done(jobId), url);
        keys.expire(done(jobId), TTL);
        streams.xack(ready(jobId), GROUP, messageId);
        return isQuiescent(jobId);
    }

    public boolean isQuiescent(UUID jobId) {
        return sets.scard(done(jobId)) >= sets.scard(seen(jobId));
    }

    public void saveRecords(UUID jobId, String url, String json) {
        hashes.hset(records(jobId), url, json);
        keys.expire(records(jobId), TTL);
    }

    public Map<String, String> readAllRecords(UUID jobId) {
        Map<String, String> all = hashes.hgetall(records(jobId));
        return all == null ? Map.of() : all;
    }

    public boolean tryBecomeFinalizer(UUID jobId) {
        boolean won = counters.setnx(finalized(jobId), 1L);
        if (won) keys.expire(finalized(jobId), TTL);
        return won;
    }

    public void cleanup(UUID jobId) {
        keys.del(ready(jobId), seen(jobId), done(jobId), pages(jobId),
                finalized(jobId), records(jobId), attempts(jobId));
    }

    private static Map<String, String> payload(CrawlNode n) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put(F_URL, n.url());
        p.put(F_DEPTH, String.valueOf(n.depth()));
        p.put(F_PARENT, n.parentUrl() == null ? "" : n.parentUrl());
        return p;
    }

    private static List<FrontierEntry> toEntries(List<StreamMessage<String, String, String>> msgs) {
        if (msgs == null || msgs.isEmpty()) return List.of();
        List<FrontierEntry> out = new ArrayList<>(msgs.size());
        for (StreamMessage<String, String, String> m : msgs) {
            Map<String, String> p = m.payload();
            String parent = p.getOrDefault(F_PARENT, "");
            CrawlNode node = new CrawlNode(
                    p.get(F_URL),
                    Integer.parseInt(p.getOrDefault(F_DEPTH, "0")),
                    parent.isBlank() ? null : parent);
            out.add(new FrontierEntry(m.id(), node));
        }
        return out;
    }

    public record FrontierEntry(String messageId, CrawlNode node) {
    }
}