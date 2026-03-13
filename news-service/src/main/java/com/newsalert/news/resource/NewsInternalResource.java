package com.newsalert.news.resource;

import com.newsalert.news.entity.SearchResult;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Internal API consumed by alert-service.
 *
 * Not exposed via any public reverse proxy – Docker-network access only.
 *
 * POST /api/internal/search-results/mark-notified
 *     Body: JSON array of URL strings
 *     Marks matching SearchResult rows as alreadyNotified = true.
 */
@Path("/api/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NewsInternalResource {

    private static final Logger LOG = Logger.getLogger(NewsInternalResource.class);

    @POST
    @Path("/search-results/mark-notified")
    @Transactional
    public Response markNotified(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Response.ok("{\"updated\":0}").build();
        }

        int updated = 0;
        for (String url : urls) {
            long count = SearchResult.update(
                    "alreadyNotified = true where url = ?1 and alreadyNotified = false",
                    url);
            updated += (int) count;
        }

        LOG.debugf("Marked %d/%d SearchResult(s) as notified", updated, urls.size());
        return Response.ok("{\"updated\":" + updated + "}").build();
    }
}
