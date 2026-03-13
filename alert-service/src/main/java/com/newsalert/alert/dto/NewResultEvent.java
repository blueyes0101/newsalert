package com.newsalert.alert.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Matches the JSON published by news-service to the "new-results" Kafka topic.
 * Fields mirror {@code com.newsalert.news.messaging.NewResultsEvent}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewResultEvent {

    public String keyword;
    public int count;
    public String timestamp;
}
