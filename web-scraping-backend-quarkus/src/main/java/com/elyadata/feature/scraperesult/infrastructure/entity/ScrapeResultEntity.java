package com.elyadata.feature.scraperesult.infrastructure.entity;

import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scrape_results")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScrapeResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "records", columnDefinition = "jsonb")
    private List<Map<String, Object>> records;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", columnDefinition = "jsonb")
    private List<String> errors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logs", columnDefinition = "jsonb")
    private List<String> logs;

    @Column(name = "pages_crawled")
    private int pagesCrawled;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "engine_used")
    private String engineUsed;

    @Column(name = "scraped_at")
    private Instant scrapedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discovered_files", columnDefinition = "jsonb")
    private List<DiscoveredFile> discoveredFiles;
}