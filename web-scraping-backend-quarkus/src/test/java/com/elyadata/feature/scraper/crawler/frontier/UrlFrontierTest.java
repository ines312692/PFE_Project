package com.elyadata.feature.scraper.crawler.frontier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UrlFrontier")
class UrlFrontierTest {

    private UrlFrontier frontier;

    @BeforeEach
    void setUp() {
        frontier = new UrlFrontier(3);
    }

    @Nested
    @DisplayName("offer()")
    class Offer {

        @Test
        @DisplayName("accepts a valid node within maxDepth")
        void acceptsValidNode() {
            boolean offered = frontier.offer(new CrawlNode("https://example.com", 0, null));
            assertThat(offered).isTrue();
            assertThat(frontier.queuedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("rejects a node whose depth exceeds maxDepth")
        void rejectsNodeBeyondMaxDepth() {
            boolean offered = frontier.offer(new CrawlNode("https://example.com/deep", 4, null));
            assertThat(offered).isFalse();
            assertThat(frontier.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("accepts node at exactly maxDepth")
        void acceptsNodeAtMaxDepth() {
            boolean offered = frontier.offer(new CrawlNode("https://example.com/d3", 3, null));
            assertThat(offered).isTrue();
        }

        @Test
        @DisplayName("rejects duplicate URL (already enqueued)")
        void rejectsDuplicateEnqueue() {
            frontier.offer(new CrawlNode("https://example.com", 0, null));
            boolean second = frontier.offer(new CrawlNode("https://example.com", 0, null));
            assertThat(second).isFalse();
            assertThat(frontier.queuedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("rejects URL that was already visited (polled)")
        void rejectsAlreadyVisited() {
            frontier.offer(new CrawlNode("https://example.com", 0, null));
            frontier.poll();                                // marks as visited
            boolean reoffered = frontier.offer(new CrawlNode("https://example.com", 0, null));
            assertThat(reoffered).isFalse();
        }

        @Test
        @DisplayName("accepts distinct URLs independently")
        void acceptsDistinctUrls() {
            frontier.offer(new CrawlNode("https://a.com", 0, null));
            frontier.offer(new CrawlNode("https://b.com", 0, null));
            assertThat(frontier.queuedCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("poll()")
    class Poll {

        @Test
        @DisplayName("returns empty when frontier is empty")
        void returnsEmptyWhenEmpty() {
            Optional<CrawlNode> node = frontier.poll();
            assertThat(node).isEmpty();
        }

        @Test
        @DisplayName("returns nodes in FIFO order")
        void returnsFifoOrder() {
            frontier.offer(new CrawlNode("https://first.com", 0, null));
            frontier.offer(new CrawlNode("https://second.com", 0, null));

            assertThat(frontier.poll()).map(CrawlNode::url).hasValue("https://first.com");
            assertThat(frontier.poll()).map(CrawlNode::url).hasValue("https://second.com");
        }

        @Test
        @DisplayName("marks polled URL as visited")
        void marksPolledAsVisited() {
            frontier.offer(new CrawlNode("https://example.com", 0, null));
            frontier.poll();
            boolean reoffered = frontier.offer(new CrawlNode("https://example.com", 0, null));
            assertThat(reoffered).isFalse();
        }
    }

    @Nested
    @DisplayName("isEmpty() / queuedCount()")
    class StateChecks {

        @Test
        @DisplayName("isEmpty() is true initially")
        void isEmptyInitially() {
            assertThat(frontier.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty() is false after offer")
        void notEmptyAfterOffer() {
            frontier.offer(new CrawlNode("https://example.com", 0, null));
            assertThat(frontier.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("queuedCount() decrements after poll")
        void queuedCountDecrements() {
            frontier.offer(new CrawlNode("https://a.com", 0, null));
            frontier.offer(new CrawlNode("https://b.com", 0, null));
            frontier.poll();
            assertThat(frontier.queuedCount()).isEqualTo(1);
        }
    }
}
