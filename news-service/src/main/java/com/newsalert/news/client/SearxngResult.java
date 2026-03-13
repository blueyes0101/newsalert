package com.newsalert.news.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single search result entry from SearXNG's JSON response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearxngResult {

    public String title;

    public String url;

    /** The snippet / excerpt – SearXNG calls this "content". */
    public String content;

    public String engine;

    @JsonProperty("publishedDate")
    public String publishedDate;
}
