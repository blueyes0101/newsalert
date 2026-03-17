package com.newsalert.alert.resource;

import com.newsalert.alert.entity.Alert;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * REST endpoints for searching Alerts using Hibernate Search.
 *
 * <p>I created this endpoint to demonstrate that we can search across multiple
 * entity types (SearchResult in news-service and Alert in alert-service). This
 * shows the flexibility of Hibernate Search for full-text search across the
 * entire application.</p>
 *
 * <ul>
 *   <li>GET /api/alerts/search – full-text search with fuzzy matching</li>
 * </ul>
 */
@Path("/api/alerts")
@Produces(MediaType.APPLICATION_JSON)
public class AlertSearchResource {

    private static final Logger LOG = Logger.getLogger(AlertSearchResource.class);

    @Inject
    SearchSession searchSession;

    // ── GET /api/alerts/search ────────────────────────────────────────────────

    /**
     * Searches for alerts by keyword with optional fuzzy matching.
     *
     * <p>This endpoint demonstrates fuzzy search capability, which tolerates
     * typos and small spelling variations. I set the edit distance to 1,
     * which allows for one character difference (insertion, deletion, or
     * substitution).</p>
     *
     * @param q the search query string
     * @param fuzzy whether to enable fuzzy matching (default: true)
     * @param page the page number for pagination (0-indexed)
     * @param size the number of results per page
     * @return list of matching alerts
     */
    @GET
    @Path("/search")
    @Blocking
    @Transactional
    public Response search(
            @QueryParam("q") @DefaultValue("") String q,
            @QueryParam("fuzzy") @DefaultValue("true") boolean fuzzy,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        if (size < 1) {
            size = 20;
        }
        if (page < 0) {
            page = 0;
        }

        int offset = page * size;

        LOG.debugf("Alert search: q='%s', fuzzy=%s, page=%d, size=%d", q, fuzzy, page, size);

        // Build the search query
        var result = searchSession.search(Alert.class)
                .where(f -> {
                    if (q == null || q.trim().isEmpty()) {
                        // Return all active alerts if no query provided
                        return f.match().field("active").matching(true);
                    }

                    // I chose to search in both keyword and user.email fields
                    // to provide comprehensive search across alert data
                    if (fuzzy) {
                        // Fuzzy search tolerates 1 character difference
                        // This catches typos like "qarkus" instead of "quarkus"
                        return f.bool(b -> b
                                .should(f.match().field("keyword").matching(q).fuzzy(1))
                                .should(f.match().field("user.email").matching(q).fuzzy(1))
                        );
                    } else {
                        // Exact match search without fuzzy tolerance
                        return f.bool(b -> b
                                .should(f.match().field("keyword").matching(q))
                                .should(f.match().field("user.email").matching(q))
                        );
                    }
                })
                // Sort by creation date, newest first
                .sort(f -> f.field("createdAt").desc())
                .fetch(offset, size);

        List<Alert> alerts = result.hits();
        long totalHits = result.total().hitCount();

        LOG.debugf("Alert search found %d total hits, returning %d results", totalHits, alerts.size());

        return Response.ok()
                .entity(new AlertSearchResponse(alerts, totalHits, page, size))
                .build();
    }

    // ── Response DTO ──────────────────────────────────────────────────────────

    /**
     * Simple response wrapper for alert search results.
     * I kept this as an inner class since it's only used by this resource.
     */
    public static class AlertSearchResponse {
        public final List<Alert> results;
        public final long totalHits;
        public final int page;
        public final int size;
        public final int totalPages;

        public AlertSearchResponse(List<Alert> results, long totalHits, int page, int size) {
            this.results = results;
            this.totalHits = totalHits;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil((double) totalHits / size);
        }
    }
}
