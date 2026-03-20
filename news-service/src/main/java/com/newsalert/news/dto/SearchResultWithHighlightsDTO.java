package com.newsalert.news.dto;

import com.newsalert.news.entity.SearchResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API-facing projection of a {@link SearchResult} entity with highlight information.
 *
 * <p>The {@code titleHighlights} and {@code snippetHighlights} fields contain fragments
 * returned by Elasticsearch with matched terms wrapped in {@code <em>} tags, e.g.
 * {@code "Starting with <em>Elasticsearch</em> in Java"}. When no fragments are returned
 * for a field (e.g. the match was in the other field), I fall back to the raw field value
 * so the client always has something to display.</p>
 *
 * <p>Highlighting requires the fields to be declared with
 * {@code @FullTextField(highlightable = Highlightable.ANY)} in the entity. Without that
 * annotation attribute, the highlight projection silently returns an empty list —
 * the search still works, but no fragments come back.</p>
 */
public class SearchResultWithHighlightsDTO {

    public Long id;
    public String title;
    public String snippet;
    public String url;
    public String source;
    public String keyword;
    public LocalDateTime discoveredAt;

    // Highlighted fragments from Elasticsearch — contain <em> tags around matched terms.
    // Falls back to a single-element list with the original field value when ES returns no fragments.
    public List<String> titleHighlights;
    public List<String> snippetHighlights;

    /**
     * Creates a DTO from the composite projection result that includes real highlight fragments.
     *
     * <p>This factory method is called from the highlight projection query in
     * {@code SearchResource.searchWithHighlights()}. The {@code titleH} and {@code snippetH}
     * lists come directly from Elasticsearch via the {@code f.highlight("title")} and
     * {@code f.highlight("snippet")} projections.</p>
     *
     * @param entity        the loaded SearchResult entity
     * @param titleH        highlight fragments for the title field (may be empty if no match in title)
     * @param snippetH      highlight fragments for the snippet field (may be empty if no match in snippet)
     * @return a fully populated DTO
     */
    public static SearchResultWithHighlightsDTO fromWithHighlights(
            SearchResult entity, List<String> titleH, List<String> snippetH) {

        SearchResultWithHighlightsDTO dto = new SearchResultWithHighlightsDTO();
        dto.id = entity.id;
        dto.title = entity.title;
        dto.snippet = entity.snippet;
        dto.url = entity.url;
        dto.source = entity.source;
        dto.keyword = entity.keyword;
        dto.discoveredAt = entity.discoveredAt;

        // Highlight fragments come back with <em> tags around matched terms by default.
        // I fall back to the original field value when ES doesn't return fragments for a field
        // (e.g. the query matched in snippet only, so title gets no highlight).
        dto.titleHighlights = (titleH != null && !titleH.isEmpty()) ? titleH : List.of(entity.title);
        dto.snippetHighlights = (snippetH != null && !snippetH.isEmpty()) ? snippetH : List.of(entity.snippet);

        return dto;
    }

    /**
     * Creates a plain DTO without highlight information, used by the basic search endpoint.
     *
     * @param entity the SearchResult entity
     * @return a populated DTO with null highlight fields
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
