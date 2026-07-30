package com.elyadata.feature.job.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crawler_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrawlerConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "max_pages", nullable = false)
    @Builder.Default
    private Integer maxPages = 100;

    @Column(name = "max_depth", nullable = false)
    @Builder.Default
    private Integer maxDepth = 2;

    @Column(name = "url_include_patterns")
    private String urlIncludePatterns;

    @Column(name = "url_exclude_patterns")
    private String urlExcludePatterns;

    @Column(name = "follow_redirects", nullable = false)
    @Builder.Default
    private Boolean followRedirects = true;

    @Column(name = "respect_robots_txt", nullable = false)
    @Builder.Default
    private Boolean respectRobotsTxt = true;

    @Column(name = "sitemap_url")
    private String sitemapUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}