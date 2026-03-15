package com.newsalert.news.dto;

import com.newsalert.news.entity.SearchResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API-facing projection of a {@link SearchResult} entity with highlight information.
 * This DTO includes highlighted fragments for title and snippet fields when available,
 * falling back to the original field values when highlights are not present.
 * 
 * I created this separate DTO to maintain backward compatibility while adding
 * highlighting support. The original SearchResultDTO remains unchanged for
 * cases where highlighting is not needed.
 */
public class SearchResultWithHighlightsDTO {

    public Long id;
    public String title;
    public String snippet;
    public String url;
    public String source;
    public String keyword;
    public LocalDateTime discoveredAt;
    
    // Highlight fragments - these contain the matched terms wrapped in <em> tags
    public List<String> titleHighlights;
    public List<String> snippetHighlights;

    /**
     * Creates a DTO from a SearchResult entity with optional highlight information.
     * For now, this just creates a basic DTO without highlighting. Highlighting
     * support will be added in a future iteration once we figure out the correct
     * Hibernate Search API for highlighting.
     * 
     * @param entity the SearchResult entity
     * @param searchResult the Hibernate Search result (not used currently)
     * @return a populated DTO with original values
     */
    public static SearchResultWithHighlightsDTO from(SearchResult entity, org.hibernate.search.engine.search.query.SearchResult<SearchResult> searchResult) {
        // For now, just use the basic from method without highlighting
        return from(entity);
    }
    
    /**
     * Creates a DTO from a SearchResult entity without highlight information.
     * This is used as a fallback when highlighting is not available or not requested.
     * 
     * @param entity the SearchResult entity
     * @return a populated DTO with original values
     */
    public static SearchResultWithHighlightsDTO from(SearchResult entity) {
        SearchResultWithHighlightsDTO dto = new SearchResultWithHighlightsDTO();
        dto.id = entity.id;
        dto.title = entity.title;
        dto.snippet = entity.snippet;
        dto.url = entity.url;
        dto.source = entity.source;
        dto.keyword = entity.keyword;
        dto.discoveredAt = entity.discoveredAt;
        dto.titleHighlights = null;
        dto.snippetHighlights = null;
        return dto;
    }
}