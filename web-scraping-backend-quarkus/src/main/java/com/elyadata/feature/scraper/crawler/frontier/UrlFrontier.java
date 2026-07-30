package com.elyadata.feature.scraper.crawler.frontier;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
public final class UrlFrontier {

    private final Deque<CrawlNode> queue = new ArrayDeque<>();
    private final Set<String> visited = new HashSet<>();
    private final Set<String> enqueued = new HashSet<>();
    private final int maxDepth;

    public UrlFrontier(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public boolean offer(CrawlNode node) {
        if (node.depth() > maxDepth) return false;
        if (visited.contains(node.url())) return false;
        if (!enqueued.add(node.url())) return false;
        queue.offerLast(node);
        return true;
    }

    public Optional<CrawlNode> poll() {
        CrawlNode n = queue.pollFirst();
        if (n != null) visited.add(n.url());
        return Optional.ofNullable(n);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int queuedCount() {
        return queue.size();
    }
}