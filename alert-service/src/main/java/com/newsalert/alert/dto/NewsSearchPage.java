package com.newsalert.alert.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors {@code com.newsalert.news.dto.SearchResponse} from news-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsSearchPage {

    public List<NewsSearchResultItem> results = new ArrayList<>();
    public long totalHits;
    public int page;
    public int size;
}
