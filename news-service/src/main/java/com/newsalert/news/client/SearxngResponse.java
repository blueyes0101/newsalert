package com.newsalert.news.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level JSON envelope returned by SearXNG at /search?format=json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearxngResponse {

    public List<SearxngResult> results = new ArrayList<>();
}
