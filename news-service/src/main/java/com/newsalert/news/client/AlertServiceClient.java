package com.newsalert.news.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Internal REST client to fetch all active keywords from the alert-service.
 * Base URL: quarkus.rest-client.alert-service.url=http://alert-service:8081
 */
@Path("/api/internal")
@RegisterRestClient(configKey = "alert-service")
public interface AlertServiceClient {

    @GET
    @Path("/keywords")
    @Produces(MediaType.APPLICATION_JSON)
    List<String> getActiveKeywords();
}
