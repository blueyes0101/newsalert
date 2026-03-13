package com.newsalert.alert.client;

import com.newsalert.alert.dto.NewsSearchPage;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for the news-service public search API.
 * Base URL: quarkus.rest-client.news-service.url=http://news-service:8080
 */
@Path("/api/search")
@RegisterRestClient(configKey = "news-service")
public interface NewsServiceClient {

    @GET
    NewsSearchPage search(
            @QueryParam("q")       String q,
            @QueryParam("keyword") String keyword,
            @QueryParam("from")    String from,        // ISO date yyyy-MM-dd
            @QueryParam("page")    @DefaultValue("0")   int page,
            @QueryParam("size")    @DefaultValue("100") int size
    );
}
