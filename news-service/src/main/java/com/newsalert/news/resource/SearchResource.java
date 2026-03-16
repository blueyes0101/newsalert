package com.newsalert.news.resource;

import com.newsalert.news.dto.SearchResponse;
import com.newsalert.news.dto.SearchResultDTO;
import com.newsalert.news.dto.SearchResultWithHighlightsDTO;
import com.newsalert.news.dto.SearchResponseWithHighlights;
import com.newsalert.news.entity.SearchResult;
import com.newsalert.news.repository.SearchResultRepository;
import io.smallrye.common.annotation.Blocking;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.engine.search.aggregation.AggregationKey;
import org.hibernate.search.engine.search.query.SearchResult;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for full-text search and index management.
 *
 * <ul>
 *   <li>GET  /api/search           – paginated full-text + filter search</li>
 *   <li>GET  /api/search/health     – ES index document count</li>
 *   <li>POST /api/search/reindex    – MassIndexer rebuild</li>
 * </ul>
 */
@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    private static final Logger LOG = Logger.getLogger(SearchResource.class);

    @Inject
    SearchSession searchSession;

    @Inject
    SearchResultRepository searchResultRepository;

    // ── GET /api/search ───────────────────────────────────────────────────────

    /**
     * Full-text search with optional keyword and date filters.
     *
     * @param q       required – search term matched against title + snippet (fuzzy)
     * @param keyword optional – exact keyword field filter (e.g. "Bitcoin")
     * @param from    optional – only results discovered on or after this date
     * @param page    page number (0-based), default 0
     * @param size    page size, default 20
     */
    @GET
    @Blocking
    @Transactional
    public SearchResponse search(
            @QueryParam("q")       @DefaultValue("") String q,
            @QueryParam("keyword")                   String keyword,
            @QueryParam("from")                      LocalDate from,
            @QueryParam("page")    @DefaultValue("0")  int page,
            @QueryParam("size")    @DefaultValue("20") int size) {

        if (q.isBlank()) {
            throw new BadRequestException("Query parameter 'q' is required and must not be blank");
        }
        if (page < 0) page = 0;
        if (size < 1 || size > 100) size = 20;

        LOG.debugf("Search: q='%s' keyword='%s' from=%s page=%d size=%d",
                q, keyword, from, page, size);

        final String effectiveKeyword = keyword;
        final LocalDate effectiveFrom = from;

        var result = searchSession
                        .search(SearchResult.class)
                        .where(f -> {
                            var bool = f.bool()
                                    .must(f.match()
                                            .fields("title", "snippet")
                                            .matching(q)
                                            .fuzzy(1));

                            if (effectiveKeyword != null && !effectiveKeyword.isBlank()) {
                                bool = bool.filter(f.match()
                                        .field("keyword")
                                        .matching(effectiveKeyword));
                            }
                            if (effectiveFrom != null) {
                                bool = bool.filter(f.range()
                                        .field("discoveredAt")
                                        .atLeast(effectiveFrom.atStartOfDay()));
                            }
                            return bool;
                        })
                        .sort(f -> f.field("discoveredAt").desc())
                        .fetch(page * size, size);

        List<SearchResultDTO> dtos = result.hits().stream()
                .map(SearchResultDTO::from)
                .toList();

        LOG.debugf("Search returned %d/%d hits for q='%s'",
                dtos.size(), result.total().hitCount(), q);

        return new SearchResponse(dtos, result.total().hitCount(), page, size);
    }

    // ── GET /api/search/highlighted ───────────────────────────────────────────────

    /**
     * Full-text search with highlighting support for matched terms.
     * Returns search results with highlighted fragments for title and snippet fields.
     * Highlighted terms are wrapped in <em> tags for easy styling in the UI.
     *
     * @param q       required – search term matched against title + snippet (fuzzy)
     * @param keyword optional – exact keyword field filter (e.g. "Bitcoin")
     * @param from    optional – only results discovered on or after this date
     * @param page    page number (0-based), default 0
     * @param size    page size, default 20
     */
    @GET
    @Path("/highlighted")
    @Blocking
    @Transactional
    public SearchResponseWithHighlights searchWithHighlights(
            @QueryParam("q")       @DefaultValue("") String q,
            @QueryParam("keyword")                   String keyword,
            @QueryParam("from")                      LocalDate from,
            @QueryParam("page")    @DefaultValue("0")  int page,
            @QueryParam("size")    @DefaultValue("20") int size) {

        if (q.isBlank()) {
            throw new BadRequestException("Query parameter 'q' is required and must not be blank");
        }
        if (page < 0) page = 0;
        if (size < 1 || size > 100) size = 20;

        LOG.debugf("Search with highlights: q='%s' keyword='%s' from=%s page=%d size=%d",
                q, keyword, from, page, size);

        final String effectiveKeyword = keyword;
        final LocalDate effectiveFrom = from;

        // Build the search query with highlighting support
        var result = searchSession
                        .search(SearchResult.class)
                        .where(f -> {
                            var bool = f.bool()
                                    .must(f.match()
                                            .fields("title", "snippet")
                                            .matching(q)
                                            .fuzzy(1));

                            if (effectiveKeyword != null && !effectiveKeyword.isBlank()) {
                                bool = bool.filter(f.match()
                                        .field("keyword")
                                        .matching(effectiveKeyword));
                            }
                            if (effectiveFrom != null) {
                                bool = bool.filter(f.range()
                                        .field("discoveredAt")
                                        .atLeast(effectiveFrom.atStartOfDay()));
                            }
                            return bool;
                        })
                        .sort(f -> f.field("discoveredAt").desc())
                        .fetch(page * size, size);

        // Convert hits to DTOs with highlight information
        List<SearchResultWithHighlightsDTO> dtos = result.hits().stream()
                .map(hit -> SearchResultWithHighlightsDTO.from(hit, result))
                .toList();

        LOG.debugf("Search with highlights returned %d/%d hits for q='%s'",
                dtos.size(), result.total().hitCount(), q);

        return new SearchResponseWithHighlights(dtos, result.total().hitCount(), page, size);
    }

    // ── GET /api/search/health ────────────────────────────────────────────────

    /**
     * Returns the number of documents currently indexed in Elasticsearch.
     * Useful for readiness checks and monitoring dashboards.
     */
    @GET
    @Path("/health")
    @Blocking
    @Transactional
    public Response indexHealth() {
        long indexedCount = searchSession
                .search(SearchResult.class)
                .where(f -> f.matchAll())
                .fetchTotalHitCount();

        long dbCount = searchResultRepository.count();

        LOG.debugf("Index health: es=%d pg=%d", indexedCount, dbCount);

        String body = String.format(
                "{\"status\":\"ok\",\"indexedDocuments\":%d,\"databaseRows\":%d}",
                indexedCount, dbCount);

        return Response.ok(body).build();
    }

    // ── POST /api/search/reindex ──────────────────────────────────────────────

    /**
     * Triggers a full MassIndexer run to rebuild the Elasticsearch index
     * from the PostgreSQL source of truth.
     */
    @POST
    @Path("/reindex")
    @Blocking
    @Transactional
    public Response reindex() {
        long total = searchResultRepository.count();
        LOG.infof("MassIndexer started – %d SearchResult(s) to reindex", total);

        try {
            searchSession.massIndexer(SearchResult.class)
                    .threadsToLoadObjects(4)
                    .startAndWait();

            LOG.infof("MassIndexer finished – %d entities reindexed", total);
            return Response.ok()
                    .entity("{\"status\":\"ok\",\"reindexed\":" + total + "}")
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("MassIndexer was interrupted", e);
            return Response.serverError()
                    .entity("{\"status\":\"error\",\"message\":\"Reindex interrupted\"}")
                    .build();
        }
    }

    // ── GET /api/search/stats ────────────────────────────────────────────────

    /**
     * Returns aggregated search result counts grouped by source.
     * Optionally accepts a keyword parameter to filter results before aggregation.
     * 
     * Uses Hibernate Search aggregations to efficiently count documents per source
     * without retrieving all individual results.
     * 
     * @param keyword optional search term to filter results (default: empty string)
     * @return Map<String, Long> where key is source name and value is document count
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    @Transactional
    public Response getStats(@QueryParam("keyword") @DefaultValue("") String keyword) {
        // Define an aggregation key for the source field
        // This tells Hibernate Search we want to aggregate on the "source" field
        AggregationKey<Map<String, Long>> countsBySourceKey = AggregationKey.of("counts_by_source");
        
        // Build the search query with aggregation
        SearchResult<SearchResult> result = searchSession.search(SearchResult.class)
                .where(f -> {
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        // If keyword is provided, filter results to only those matching the keyword
                        // We search in title, snippet, and url fields for maximum coverage
                        return f.bool(b -> b
                            .should(f.match().field("title").matching(keyword))
                            .should(f.match().field("snippet").matching(keyword))
                            .should(f.match().field("url").matching(keyword))
                        );
                    } else {
                        // If no keyword, match all documents
                        return f.matchAll();
                    }
                })
                // Add aggregation: count documents grouped by source field
                // The "source" field is defined as @KeywordField in SearchResult entity
                // which makes it perfect for exact-value aggregations
                .aggregation(countsBySourceKey, f -> f.terms().field("source", String.class))
                .fetch(0, 0); // We don't need actual hits, just the aggregation results
        
        // Extract the aggregation results from the search result
        // The Map contains source names as keys and document counts as values
        Map<String, Long> countsBySource = result.aggregation(countsBySourceKey);
        
        LOG.debugf("Stats endpoint called with keyword='%s', returning %d sources", 
                   keyword, countsBySource.size());
        
        return Response.ok(countsBySource).build();
    }
}
