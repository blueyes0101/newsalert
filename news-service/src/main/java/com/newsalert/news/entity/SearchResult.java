package com.newsalert.news.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_results")
@Indexed
public class SearchResult extends PanacheEntity {

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
