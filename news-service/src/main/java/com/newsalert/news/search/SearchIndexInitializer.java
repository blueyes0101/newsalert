package com.newsalert.news.search;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.jboss.logging.Logger;

/**
 * Startup observer that automatically triggers Hibernate Search mass indexer
 * when the Elasticsearch index is empty on application startup.
 * 
 * This ensures that the search index is populated with existing data from the
 * database when the application starts for the first time or after index corruption.
 */
@ApplicationScoped
public class SearchIndexInitializer {

    private static final Logger LOG = Logger.getLogger(SearchIndexInitializer.class);

    @Inject
    SearchSession searchSession;

    @ConfigProperty(name = "newsalert.search.auto-reindex-on-empty", defaultValue = "true")
    boolean autoReindexOnEmpty;

    @ConfigProperty(name = "newsalert.search.reindex-threads", defaultValue = "2")
    int reindexThreads;

    @ConfigProperty(name = "newsalert.search.reindex-batch-size", defaultValue = "25")
    int reindexBatchSize;

    /**
     * Observes application startup event and checks if Elasticsearch index is empty.
     * If the index contains no documents, triggers a mass reindexing operation.
     * 
     * I implemented this because we noticed that new deployments often start with
     * an empty Elasticsearch index, making search functionality unavailable until
     * manual reindexing is performed.
     */
    @Transactional
    public void onStart(@Observes StartupEvent event) {
        // Skip auto-reindexing if disabled via configuration
        if (!autoReindexOnEmpty) {
            LOG.info("Auto-reindexing on startup is disabled via configuration");
            return;
        }
        
        try {
            LOG.info("Checking Elasticsearch index status on startup...");
            
            // Count total documents in the search index
            long documentCount = searchSession.search(com.newsalert.news.entity.SearchResult.class)
                    .where(f -> f.matchAll())
                    .fetchTotalHitCount();
            
            LOG.infof("Found %d documents in Elasticsearch index", documentCount);
            
            if (documentCount == 0) {
                LOG.info("Elasticsearch index is empty, triggering automatic reindexing...");
                performMassIndexing();
            } else {
                LOG.info("Elasticsearch index already contains data, skipping automatic reindexing");
            }
            
        } catch (Exception e) {
            LOG.error("Failed to check or initialize Elasticsearch index", e);
            // Don't fail startup if Elasticsearch is not available - the application
            // should still work with database-only queries as fallback
        }
    }

    /**
     * Performs mass indexing of all SearchResult entities from the database
     * into the Elasticsearch index. This operation can take significant time
     * for large datasets but ensures complete index synchronization.
     * 
     * I chose to use MassIndexer here because it's the most efficient way to
     * bulk index existing data, especially when dealing with thousands of records.
     * The thread count and batch size are configurable via application.properties
     * to allow tuning based on system resources and dataset size.
     */
    private void performMassIndexing() {
        try {
            LOG.infof("Starting mass reindexing operation with %d threads and batch size %d", 
                      reindexThreads, reindexBatchSize);
            
            MassIndexer indexer = searchSession.massIndexer(com.newsalert.news.entity.SearchResult.class)
                    .threadsToLoadObjects(reindexThreads) // Configurable thread count
                    .batchSizeToLoadObjects(reindexBatchSize); // Configurable batch size
            
            // Start the indexing operation and wait for completion
            indexer.startAndWait();
            
            // Verify the indexing completed successfully
            long finalCount = searchSession.search(com.newsalert.news.entity.SearchResult.class)
                    .where(f -> f.matchAll())
                    .fetchTotalHitCount();
            
            LOG.infof("Reindexing complete, indexed %d entities", finalCount);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Mass indexing was interrupted", e);
            throw new RuntimeException("Mass indexing interrupted", e);
        } catch (Exception e) {
            LOG.error("Mass indexing failed", e);
            throw new RuntimeException("Failed to perform mass indexing", e);
        }
    }
}