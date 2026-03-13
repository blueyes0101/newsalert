package com.newsalert.news.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Quarkus REST client for the local SearXNG meta-search engine.
 *
 * <p>Base URL is configured via:
 * <pre>quarkus.rest-client.searxng.url=http://searxng:8888</pre>
 *
 * <p>Callers should wrap invocations in try/catch – SearXNG may be
 * temporarily unavailable during container start-up or network hiccups.
 */
@Path("/search")
@RegisterRestClient(configKey = "searxng")
public interface SearxngClient {

    /**
     * Executes a search query against SearXNG and returns parsed JSON results.
     *
     * @param query  the search term (e.g. "Bitcoin", "Iran nuclear")
     * @param format must be {@code "json"} – SearXNG only returns structured
     *               data when the JSON format is explicitly requested
     * @return parsed response containing a list of {@link SearxngResult}s
     */
    @GET
    SearxngResponse search(
            @QueryParam("q") String query,
            @QueryParam("format") String format
    );
}
