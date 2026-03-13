package com.newsalert.alert.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * REST client for the news-service internal API.
 * Marks SearchResult rows as alreadyNotified after emails are sent.
 * Base URL: quarkus.rest-client.news-service.url=http://news-service:8080
 */
@Path("/api/internal")
@RegisterRestClient(configKey = "news-service")
public interface NewsServiceInternalClient {

    @POST
    @Path("/search-results/mark-notified")
    @Consumes(MediaType.APPLICATION_JSON)
    void markNotified(List<String> urls);
}
