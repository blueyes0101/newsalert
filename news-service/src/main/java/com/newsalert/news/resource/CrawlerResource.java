package com.newsalert.news.resource;

import com.newsalert.news.client.AlertServiceClient;
import com.newsalert.news.scheduler.CrawlerService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Internal endpoint to trigger a crawler cycle on demand.
 * Useful for testing and manual verification without waiting 15 minutes.
 */
@Path("/api/crawler")
@Produces(MediaType.APPLICATION_JSON)
public class CrawlerResource {

    private static final Logger LOG = Logger.getLogger(CrawlerResource.class);

    @Inject
    CrawlerService crawlerService;

    @RestClient
    AlertServiceClient alertServiceClient;

    /**
     * Triggers an immediate crawl cycle for all active keywords.
     * Returns a summary of how many keywords were processed.
     */
    @POST
    @Path("/run")
    @Blocking
    public Response runCrawl() {
        LOG.info("Manual crawler run requested via REST");

        List<String> keywords;
        try {
            keywords = alertServiceClient.getActiveKeywords();
            if (keywords == null) keywords = Collections.emptyList();
        } catch (Exception e) {
            LOG.warnf("Could not fetch keywords from alert-service: %s", e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Could not fetch keywords: " + e.getMessage()))
                    .build();
        }

        if (keywords.isEmpty()) {
            return Response.ok(Map.of("message", "No active keywords found", "processed", 0)).build();
        }

        int processed = 0;
        int errors = 0;
        for (String keyword : keywords) {
            try {
                crawlerService.crawlKeyword(keyword);
                processed++;
            } catch (Exception e) {
                LOG.errorf("Error crawling keyword='%s': %s", keyword, e.getMessage());
                errors++;
            }
        }

        return Response.ok(Map.of(
                "message",    "Crawler run complete",
                "keywords",   keywords,
                "processed",  processed,
                "errors",     errors
        )).build();
    }
}
