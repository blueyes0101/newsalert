package com.newsalert.news.scheduler;

import com.newsalert.news.client.AlertServiceClient;
import com.newsalert.news.client.SearxngResult;
import com.newsalert.news.client.SearxngService;
import com.newsalert.news.entity.SearchResult;
import com.newsalert.news.messaging.NewResultsProducer;
import com.newsalert.news.repository.SearchResultRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Scheduled crawler: every 15 minutes it fetches all active keywords from
 * the alert-service, queries SearXNG for each one, deduplicates against
 * PostgreSQL, persists new results (which are auto-indexed in Elasticsearch),
 * and publishes a Kafka summary event per keyword.
 */
@ApplicationScoped
public class CrawlerService {

    private static final Logger LOG = Logger.getLogger(CrawlerService.class);

    @Inject
    SearxngService searxngService;

    @RestClient
    AlertServiceClient alertServiceClient;

    @Inject
    SearchResultRepository searchResultRepository;

    @Inject
    NewResultsProducer newResultsProducer;

    // ── Scheduled entry point ─────────────────────────────────────────────────

    @Scheduled(every = "15m", identity = "crawler-job")
    void crawl() {
        LOG.info("Crawler cycle started");

        List<String> keywords = fetchKeywords();
        if (keywords.isEmpty()) {
            LOG.info("No active keywords found – skipping crawl cycle");
            return;
        }

        LOG.infof("Crawling %d keyword(s): %s", keywords.size(), keywords);

        for (String keyword : keywords) {
            try {
                crawlKeyword(keyword);
            } catch (Exception e) {
                // One bad keyword must not abort the whole cycle
                LOG.errorf("Unexpected error while crawling keyword='%s': %s",
                        keyword, e.getMessage());
            }
        }

        LOG.info("Crawler cycle finished");
    }

    // ── Per-keyword crawl ─────────────────────────────────────────────────────

    /**
     * Fetches SearXNG results, persists new ones in a transaction, then (after
     * the transaction commits) publishes a Kafka event.  The Kafka send must
     * happen outside the @Transactional boundary to avoid Narayana detecting
     * multiple threads inside the same JTA transaction.
     */
    public void crawlKeyword(String keyword) {
        int newCount = persistNewResults(keyword);
        newResultsProducer.sendNewResults(keyword, newCount);
    }

    @Transactional
    public int persistNewResults(String keyword) {
        LOG.infof("Crawling keyword='%s'", keyword);

        List<SearxngResult> results = searxngService.search(keyword);
        LOG.infof("SearXNG returned %d result(s) for keyword='%s'",
                results.size(), keyword);

        int newCount = 0;
        for (SearxngResult result : results) {
            if (result.url == null || result.url.isBlank()) {
                continue;
            }
            if (searchResultRepository.existsByUrl(result.url)) {
                LOG.debugf("Duplicate – skipping url='%s'", result.url);
                continue;
            }

            SearchResult entity = toEntity(result, keyword);
            searchResultRepository.persist(entity);
            newCount++;
            LOG.debugf("Saved new result: url='%s' title='%s'",
                    result.url, result.title);
        }

        LOG.infof("Saved %d new result(s) for keyword='%s'", newCount, keyword);
        return newCount;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> fetchKeywords() {
        try {
            List<String> keywords = alertServiceClient.getActiveKeywords();
            return keywords != null ? keywords : Collections.emptyList();
        } catch (Exception e) {
            LOG.warnf("Could not fetch keywords from alert-service: %s", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SearchResult toEntity(SearxngResult r, String keyword) {
        SearchResult entity = new SearchResult();
        entity.title = r.title != null ? r.title : "";
        entity.snippet = r.content != null ? r.content : "";
        entity.url = r.url;
        entity.source = r.engine != null ? r.engine : "searxng";
        entity.keyword = keyword;
        return entity;
    }
}
