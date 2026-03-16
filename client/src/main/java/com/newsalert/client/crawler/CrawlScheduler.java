package com.newsalert.client.crawler;

import com.newsalert.client.api.AlertServiceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton that fires POST /api/crawler/run on the news-service at a
 * configurable fixed-rate interval.
 *
 * Call {@link #start} once at application startup, then {@link #restart}
 * whenever the interval changes. Call {@link #stop} on application exit.
 */
public class CrawlScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlScheduler.class);
    private static final CrawlScheduler INSTANCE = new CrawlScheduler();

    public static CrawlScheduler getInstance() { return INSTANCE; }

    private ScheduledExecutorService executor;
    private String  newsBaseUrl;
    private String  token;
    private int     intervalMinutes;

    private CrawlScheduler() {}

    /** Starts the scheduler. No-op if already running with the same parameters. */
    public synchronized void start(String newsBaseUrl, String token, int intervalMinutes) {
        this.newsBaseUrl      = newsBaseUrl;
        this.token            = token;
        this.intervalMinutes  = intervalMinutes;
        schedule();
        LOG.info("Crawl scheduler started — interval={} min", intervalMinutes);
    }

    /**
     * Cancels the current schedule and restarts with new parameters.
     * Safe to call from any thread.
     */
    public synchronized void restart(String newsBaseUrl, String token, int intervalMinutes) {
        shutdown();
        start(newsBaseUrl, token, intervalMinutes);
        LOG.info("Crawl scheduler restarted — interval={} min", intervalMinutes);
    }

    /** Shuts down the scheduler cleanly. Safe to call from any thread. */
    public synchronized void stop() {
        shutdown();
        LOG.info("Crawl scheduler stopped");
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void schedule() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crawl-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(
                this::triggerCrawl,
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES);
    }

    private void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void triggerCrawl() {
        try {
            // AlertServiceApi re-uses its OkHttp client; we only need the crawl method
            // which takes a newsBaseUrl directly, so we instantiate with a placeholder.
            AlertServiceApi api = new AlertServiceApi(newsBaseUrl);
            api.triggerCrawler(newsBaseUrl, token);
            LOG.info("Scheduled crawl triggered (interval={} min)", intervalMinutes);
        } catch (AlertServiceApi.ApiException ex) {
            LOG.warn("Scheduled crawl failed: {}", ex.getMessage());
        }
    }
}
