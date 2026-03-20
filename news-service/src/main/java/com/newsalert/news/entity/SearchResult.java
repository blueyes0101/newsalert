package com.newsalert.news.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.LocalDateTime;

/**
 * Entity representing a crawled news search result.
 *
 * <p>This entity is indexed by Hibernate Search for full-text search capabilities.
 * Both PostgreSQL (via Hibernate ORM) and Elasticsearch (via Hibernate Search) are
 * kept in sync automatically: when a {@code SearchResult} is persisted inside a
 * {@code @Transactional} method, the JPA event listener fires and sends the document
 * to Elasticsearch on transaction commit.</p>
 *
 * <h3>Field Annotation Strategy</h3>
 * <ul>
 *   <li>{@code @FullTextField} — analysed by the {@code news_analyzer} (see
 *       {@code NewsAnalysisConfigurer}): standard tokenisation, lowercase, ASCII-folding,
 *       English stemming. Also declared {@code highlightable} so Elasticsearch can return
 *       highlighted fragments with {@code <em>} tags around matched terms.</li>
 *   <li>{@code @KeywordField} — stored verbatim (no analysis). Used for exact-match
 *       filters (keyword, source) and deduplication (url).</li>
 *   <li>{@code @GenericField(sortable = YES)} — doc-values column in Elasticsearch,
 *       required for efficient {@code sort()} operations.</li>
 * </ul>
 */
@Entity
@Table(name = "search_results")
@Indexed
public class SearchResult extends PanacheEntity {

    // Analysed by news_analyzer (lowercase + asciifolding + English stemming).
    // Highlightable.ANY lets Elasticsearch return <em>-wrapped fragments for these fields.
    @FullTextField(highlightable = Highlightable.ANY)
    @Column(name = "title", length = 512)
    public String title;

    @FullTextField(highlightable = Highlightable.ANY)
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
