package com.newsalert.news.dto;

import java.util.List;

/**
 * Paginated search response envelope.
 */
public class SearchResponse {

    public List<SearchResultDTO> results;
    public long totalHits;
    public int page;
    public int size;

    public SearchResponse(List<SearchResultDTO> results, long totalHits, int page, int size) {
        this.results = results;
        this.totalHits = totalHits;
        this.page = page;
        this.size = size;
    }
}
