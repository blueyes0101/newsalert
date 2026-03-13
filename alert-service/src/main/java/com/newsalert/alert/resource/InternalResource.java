package com.newsalert.alert.resource;

import com.newsalert.alert.entity.Alert;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Internal API consumed by the news-service crawler.
 *
 * No authentication – access should be restricted to the Docker internal
 * network (news-service → alert-service:8081). Do not expose this path
 * via any public-facing reverse proxy.
 *
 * GET /api/internal/keywords – returns all unique active keywords as a
 *                              flat JSON array of strings.
 */
@Path("/api/internal")
@Produces(MediaType.APPLICATION_JSON)
public class InternalResource {

    @GET
    @Path("/keywords")
    @Transactional
    public List<String> activeKeywords() {
        return Alert.findAllActive()
                .stream()
                .map(a -> a.keyword)
                .distinct()
                .sorted()
                .toList();
    }
}
