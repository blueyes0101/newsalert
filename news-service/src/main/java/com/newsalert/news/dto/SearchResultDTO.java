package com.newsalert.news.dto;

import com.newsalert.news.entity.SearchResult;

import java.time.LocalDateTime;

/**
 * API-facing projection of a {@link SearchResult} entity.
 * The entity is never exposed directly to avoid leaking internal fields.
 */
public class SearchResultDTO {

    public Long id;
    public String title;
    public String snippet;
    public String url;
    public String source;
    public String keyword;
    public LocalDateTime discoveredAt;

    public static SearchResultDTO from(SearchResult e) {
        SearchResultDTO dto = new SearchResultDTO();
        dto.id = e.id;
        dto.title = e.title;
        dto.snippet = e.snippet;
        dto.url = e.url;
        dto.source = e.source;
        dto.keyword = e.keyword;
        dto.discoveredAt = e.discoveredAt;
        return dto;
    }
}
