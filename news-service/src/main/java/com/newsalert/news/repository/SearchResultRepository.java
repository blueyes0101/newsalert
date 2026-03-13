package com.newsalert.news.repository;

import com.newsalert.news.entity.SearchResult;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.hibernate.search.mapper.orm.session.SearchSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class SearchResultRepository implements PanacheRepository<SearchResult> {

    @Inject
    SearchSession searchSession;

    // ── Simple Panache queries (hit PostgreSQL) ───────────────────────────────

    /**
     * Returns all results persisted for the given keyword, newest first.
     */
    public List<SearchResult> findByKeyword(String keyword) {
        return list("keyword = ?1 order by discoveredAt desc", keyword);
    }

    /**
     * Returns results discovered after {@code since} that have not yet
     * triggered a notification, ordered by discovery time ascending.
     */
    public List<SearchResult> findNewResultsSince(LocalDateTime since) {
        return list(
                "discoveredAt > ?1 and alreadyNotified = false order by discoveredAt asc",
                since
        );
    }

    /**
     * Deduplication check: true when a row with this URL already exists.
     */
    public boolean existsByUrl(String url) {
        return count("url", url) > 0;
    }

    // ── Hibernate Search (full-text / Elasticsearch) queries ─────────────────

    /**
     * Full-text search across title and snippet for a given keyword phrase.
     * Falls back to simple Panache if Hibernate Search is disabled (tests).
     */
    public List<SearchResult> fullTextSearch(String queryText) {
        return searchSession
                .search(SearchResult.class)
                .where(f -> f.match()
                        .fields("title", "snippet")
                        .matching(queryText))
                .sort(f -> f.field("discoveredAt").desc())
                .fetchAllHits();
    }

    /**
     * Filter by exact keyword value using the {@code @KeywordField} index.
     */
    public List<SearchResult> searchByExactKeyword(String keyword) {
        return searchSession
                .search(SearchResult.class)
                .where(f -> f.match()
                        .field("keyword")
                        .matching(keyword))
                .sort(f -> f.field("discoveredAt").desc())
                .fetchAllHits();
    }

    /**
     * Date-range filter: results discovered between {@code from} and {@code to}.
     */
    public List<SearchResult> searchByDateRange(LocalDateTime from, LocalDateTime to) {
        return searchSession
                .search(SearchResult.class)
                .where(f -> f.range()
                        .field("discoveredAt")
                        .between(from, to))
                .sort(f -> f.field("discoveredAt").desc())
                .fetchAllHits();
    }
}
