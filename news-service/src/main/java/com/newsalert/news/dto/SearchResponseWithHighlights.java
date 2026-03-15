package com.newsalert.news.dto;

import java.util.List;

/**
 * API response wrapper for search results that include highlighting information.
 * This response includes the total hit count, pagination information, and a list
 * of search results with highlighted fragments for matched terms.
 * 
 * I created this separate response class to maintain backward compatibility
 * while adding highlighting support. The original SearchResponse remains
 * unchanged for cases where highlighting is not needed.
 */
public class SearchResponseWithHighlights {

    public final List<SearchResultWithHighlightsDTO> hits;
    public final long total;
    public final int page;
    public final int size;

    public SearchResponseWithHighlights(List<SearchResultWithHighlightsDTO> hits, long total, int page, int size) {
        this.hits = hits;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}