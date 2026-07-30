package com.elyadata.feature.job.infrastructure.entity;

import com.elyadata.feature.job.domain.model.PageAction;
import com.elyadata.feature.job.domain.model.PaginationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scraper_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScraperConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seed_urls", columnDefinition = "text")
    private List<String> seedUrls;

    @Column(name = "single_page_mode", nullable = false)
    @Builder.Default
    private Boolean singlePageMode = true;

    @Column(name = "wait_for_selector")
    private String waitForSelector;

    @Column(name = "scroll_to_bottom", nullable = false)
    @Builder.Default
    private Boolean scrollToBottom = false;

    @Column(name = "click_before_extract")
    private String clickBeforeExtract;

    @Column(name = "llm_extraction_prompt", columnDefinition = "TEXT")
    private String llmExtractionPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "pagination_type", length = 30)
    private PaginationType paginationType;

    @Column(name = "pagination_selector", length = 500)
    private String paginationSelector;

    @Column(name = "max_pagination_pages")
    private Integer maxPaginationPages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pre_extract_actions", columnDefinition = "jsonb")
    private List<PageAction> preExtractActions;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}