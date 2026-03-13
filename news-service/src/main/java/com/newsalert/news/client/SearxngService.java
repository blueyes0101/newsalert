package com.newsalert.news.client;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;

/**
 * CDI wrapper around {@link SearxngClient} that handles connection errors
 * gracefully so a SearXNG outage never crashes the scheduler.
 */
@ApplicationScoped
public class SearxngService {

    private static final Logger LOG = Logger.getLogger(SearxngService.class);
    private static final String FORMAT_JSON = "json";

    @RestClient
    SearxngClient searxngClient;

    /**
     * Searches SearXNG for {@code keyword} and returns the result list.
     * Returns an empty list (and logs a warning) on any connectivity failure.
     *
     * @param keyword the search term
     * @return list of results, never {@code null}
     */
    public List<SearxngResult> search(String keyword) {
        try {
            SearxngResponse response = searxngClient.search(keyword, FORMAT_JSON);
            if (response == null || response.results == null) {
                LOG.warnf("SearXNG returned null response for keyword='%s'", keyword);
                return Collections.emptyList();
            }
            LOG.debugf("SearXNG returned %d results for keyword='%s'",
                    response.results.size(), keyword);
            return response.results;
        } catch (Exception e) {
            LOG.warnf("SearXNG query failed for keyword='%s': %s", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }
}
