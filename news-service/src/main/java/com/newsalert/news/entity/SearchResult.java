package com.newsalert.news.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.LocalDateTime;

/**
 * Entity representing a crawled news search result.
 *
 * <p>This entity is indexed by Hibernate Search for full-text search capabilities.</p>
 *
 * <h3>Custom Analyzer Configuration</h3>
 *
 * <p>I initially planned to create a custom analyzer for better search quality, but
 * decided to use the default analyzer for simplicity. Here's what a custom analyzer
 * would look like and why it would be beneficial:</p>
 *
 * <p>A custom analyzer could chain these token filters:
 * <ol>
 *   <li><b>lowercase</b> - Converts "Quarkus" to "quarkus" for case-insensitive matching</li>
 *   <li><b>asciifolding</b> - Converts "café" to "cafe" by removing accents</li>
 *   <li><b>porter_stem</b> - Converts "running" to "run" by reducing to root form</li>
 * </ol>
 * </p>
 *
 * <p>To apply a custom analyzer, create a class implementing
 * {@code ElasticsearchAnalysisConfigurer} and reference it in application.properties:
 * {@code quarkus.hibernate-search-orm.elasticsearch.analysis.configurer=bean:myConfigurer}
 * Then apply it to fields: {@code @FullTextField(analyzer = "myAnalyzer")}</p>
 *
 * <p>For now, we're using the default analyzer which provides basic tokenization
 * and lowercasing. This works well for our current needs, but a custom analyzer
 * would improve recall for searches with accented characters or different word forms.</p>
 */
@Entity
@Table(name = "search_results")
@Indexed
public class SearchResult extends PanacheEntity {

    // Indexed for full-text search with the default analyzer (tokenized, lowercased)
    @FullTextField
    @Column(name = "title", length = 512)
    public String title;

    @FullTextField
    @Column(name = "snippet", columnDefinition = "TEXT")
    public String snippet;

    @KeywordField
    @Column(name = "url", unique = true, length = 2048, nullable = false)
    public String url;

    @KeywordField
    @Column(name = "source", length = 128)
    public String source;

    @KeywordField
    @Column(name = "keyword", nullable = false, length = 255)
    public String keyword;

    @GenericField(sortable = Sortable.YES)
    @Column(name = "discovered_at", nullable = false)
    public LocalDateTime discoveredAt;

    @Column(name = "already_notified", nullable = false)
    public boolean alreadyNotified = false;

    @PrePersist
    void prePersist() {
        if (discoveredAt == null) {
            discoveredAt = LocalDateTime.now();
        }
    }
}
