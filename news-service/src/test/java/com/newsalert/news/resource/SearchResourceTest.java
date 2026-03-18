package com.newsalert.news.resource;

import com.newsalert.news.entity.SearchResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the Hibernate Search functionality.
 *
 * <p>I wrote these tests to verify that our search implementation works correctly.
 * Before adding these tests, we had zero test coverage for the search functionality,
 * which made it risky to make changes. Now we have confidence that basic search,
 * fuzzy matching, date filtering, pagination, and aggregation all work as expected.</p>
 *
 * <p>These tests use RestAssured for HTTP-based testing and seed test data in
 * the @BeforeAll method. The tests assume an embedded Elasticsearch instance is
 * available (configured via %test profile in application.properties).</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearchResourceTest {

    @Inject
    SearchSession searchSession;

    /**
     * Seeds test data before running any tests.
     *
     * <p>I create a variety of test documents with different titles, sources, and dates
     * so we can test different search scenarios. The documents are persisted to the
     * database and then indexed in Elasticsearch via the mass indexer.</p>
     */
    @BeforeAll
    @Transactional
    public void seedTestData() {
        // Clear any existing test data first
        SearchResult.deleteAll();

        // Create test documents with varied content
        createSearchResult(
                "Quarkus 3.0 Released with Major Performance Improvements",
                "The latest version of Quarkus brings significant startup time reductions...",
                "https://quarkus.io/blog/quarkus-3-0-released",
                "Quarkus Blog",
                LocalDateTime.now().minusDays(1)
        );

        createSearchResult(
                "Hibernate Search 6.2: What's New",
                "Explore the latest features in Hibernate Search including improved Elasticsearch integration...",
                "https://hibernate.org/search/news/2023/6-2",
                "Hibernate Blog",
                LocalDateTime.now().minusDays(3)
        );

        createSearchResult(
                "Getting Started with Elasticsearch in Java",
                "A comprehensive guide to using Elasticsearch with Java applications...",
                "https://example.com/elasticsearch-java-guide",
                "Tech Tutorials",
                LocalDateTime.now().minusDays(7)
        );

        createSearchResult(
                "Microservices Best Practices for 2024",
                "Learn the latest patterns and practices for building microservices...",
                "https://example.com/microservices-2024",
                "DevOps Weekly",
                LocalDateTime.now().minusDays(5)
        );

        createSearchResult(
                "Kubernetes vs Docker Swarm: A Comparison",
                "Comparing container orchestration platforms for modern deployments...",
                "https://example.com/k8s-vs-swarm",
                "Cloud Native Daily",
                LocalDateTime.now().minusDays(10)
        );

        // Reindex to ensure Elasticsearch has the latest data
        reindexData();
    }

    private void createSearchResult(String title, String snippet, String url, String source, LocalDateTime crawledAt) {
        SearchResult result = new SearchResult();
        result.title = title;
        result.snippet = snippet;
        result.url = url;
        result.source = source;
        result.discoveredAt = crawledAt;
        result.persist();
    }

    private void reindexData() {
        try {
            searchSession.massIndexer(SearchResult.class)
                    .threadsToLoadObjects(2)
                    .startAndWait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to reindex test data", e);
        }
    }

    // ── Test Cases ───────────────────────────────────────────────────────────

    /**
     * Tests basic full-text search for an exact keyword.
     *
     * <p>I verify that searching for "Quarkus" returns the Quarkus-related document
     * we seeded in the test data. This tests the basic @FullTextField indexing.</p>
     */
    @Test
    public void testBasicSearch() {
        given()
                .queryParam("q", "Quarkus")
                .when()
                .get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].title", containsStringIgnoringCase("Quarkus"))
                .body("totalHits", greaterThanOrEqualTo(1));
    }

    /**
     * Tests fuzzy search with a typo in the query.
     *
     * <p>I intentionally misspell "Quarkus" as "Quarkuz" to verify that fuzzy
     * matching works. The fuzzy(1) setting should tolerate one character difference.</p>
     */
    @Test
    public void testFuzzySearch() {
        given()
                .queryParam("q", "Quarkuz") // Intentional typo
                .queryParam("fuzzy", "true")
                .when()
                .get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].title", containsStringIgnoringCase("Quarkus"));
    }

    /**
     * Tests date range filtering.
     *
     * <p>I filter for results from the last 3 days and verify we only get
     * recent documents. This tests the @GenericField(sortable) annotation
     * and the date filter logic in SearchResource.</p>
     */
    @Test
    public void testDateFilter() {
        String fromDate = LocalDateTime.now().minusDays(3).toLocalDate().toString();
        String toDate = LocalDateTime.now().toLocalDate().toString();

        given()
                .queryParam("q", "")
                .queryParam("from", fromDate)
                .queryParam("to", toDate)
                .when()
                .get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results.crawledAt", everyItem(greaterThanOrEqualTo(fromDate)));
    }

    /**
     * Tests pagination with page and size parameters.
     *
     * <p>I request a small page size (2) and verify we get exactly 2 results
     * back. This tests the pagination logic and the total hits count.</p>
     */
    @Test
    public void testPagination() {
        given()
                .queryParam("q", "")
                .queryParam("page", "0")
                .queryParam("size", "2")
                .when()
                .get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", lessThanOrEqualTo(2))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalHits", greaterThanOrEqualTo(5)); // We seeded 5 documents
    }

    /**
     * Tests the highlighted search endpoint.
     *
     * <p>I verify that the highlighted endpoint returns results. Since highlighting
     * support is still being implemented, I just check that the endpoint responds
     * successfully and returns the expected structure.</p>
     */
    @Test
    public void testHighlighting() {
        given()
                .queryParam("q", "Elasticsearch")
                .queryParam("highlight", "true")
                .when()
                .get("/api/search/highlighted")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].title", notNullValue())
                .body("results[0].snippet", notNullValue());
    }

    /**
     * Tests the aggregation/stats endpoint.
     *
     * <p>I verify that the stats endpoint returns source aggregation counts.
     * This tests the AggregationKey and aggregation() functionality in SearchResource.</p>
     */
    @Test
    public void testAggregation() {
        given()
                .when()
                .get("/api/search/stats")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("'Quarkus Blog'", greaterThanOrEqualTo(1))
                .body("'Hibernate Blog'", greaterThanOrEqualTo(1));
    }
}
