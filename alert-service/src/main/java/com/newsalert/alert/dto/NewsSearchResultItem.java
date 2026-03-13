package com.newsalert.alert.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Mirrors {@code com.newsalert.news.dto.SearchResultDTO} from news-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsSearchResultItem {

    public Long id;
    public String title;
    public String snippet;
    public String url;
    public String source;
    public String keyword;
    public LocalDateTime discoveredAt;
}
