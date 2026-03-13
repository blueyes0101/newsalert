package com.newsalert.news.messaging;

import java.time.Instant;

/**
 * Kafka payload published to the "new-results" topic after each crawl cycle.
 */
public class NewResultsEvent {

    public String keyword;
    public int count;
    public String timestamp;

    public NewResultsEvent() {
    }

    public NewResultsEvent(String keyword, int count) {
        this.keyword = keyword;
        this.count = count;
        this.timestamp = Instant.now().toString();
    }
}
