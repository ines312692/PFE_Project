package com.elyadata.feature.scraper.crawler.frontier;

public record CrawlNode(String url, int depth, String parentUrl) {
    public CrawlNode child(String childUrl) {
        return new CrawlNode(childUrl, depth + 1, url);
    }
}