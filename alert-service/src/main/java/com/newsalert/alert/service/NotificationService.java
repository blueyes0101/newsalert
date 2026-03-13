package com.newsalert.alert.service;

import com.newsalert.alert.client.NewsServiceClient;
import com.newsalert.alert.client.NewsServiceInternalClient;
import com.newsalert.alert.dto.NewsSearchPage;
import com.newsalert.alert.dto.NewsSearchResultItem;
import com.newsalert.alert.entity.Alert;
import com.newsalert.alert.entity.NotificationLog;
import com.newsalert.alert.ws.WebSocketBroadcaster;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Core notification logic: fetches new search results from news-service,
 * deduplicates against NotificationLog, sends emails, persists logs,
 * marks results as notified, and broadcasts to WebSocket clients.
 */
@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_BROADCAST_TITLES = 10;

    @RestClient
    NewsServiceClient newsServiceClient;

    @RestClient
    NewsServiceInternalClient newsServiceInternalClient;

    @Inject
    Mailer mailer;

    @Inject
    @Location("alert-email.html")
    Template alertEmailTemplate;

    @Inject
    WebSocketBroadcaster broadcaster;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Called by the Kafka consumer.
     * Processes all active alerts for {@code keyword}, then broadcasts
     * a single WebSocket message summarising what was found.
     */
    @Transactional
    public void processKeywordEvent(String keyword) {
        LOG.infof("Processing notification event for keyword='%s'", keyword);

        List<Alert> alerts = Alert.findActiveByKeyword(keyword);
        if (alerts.isEmpty()) {
            LOG.debugf("No active alerts for keyword='%s', skipping", keyword);
            return;
        }

        List<String> allUrls   = new ArrayList<>();
        List<String> allTitles = new ArrayList<>();

        for (Alert alert : alerts) {
            try {
                NotifiedBatch batch = processAlert(alert, keyword);
                allUrls.addAll(batch.urls());
                allTitles.addAll(batch.titles());
            } catch (Exception e) {
                LOG.errorf("Failed to process alert id=%d user='%s': %s",
                        alert.id, alert.user.email, e.getMessage());
            }
        }

        if (!allUrls.isEmpty()) {
            markNotifiedInNewsService(allUrls);

            // Deduplicate titles (same article may be found for multiple users)
            // and cap at MAX_BROADCAST_TITLES to keep WS payloads small.
            List<String> broadcastTitles = allTitles.stream()
                    .distinct()
                    .limit(MAX_BROADCAST_TITLES)
                    .toList();

            broadcaster.broadcast(keyword, allUrls.size(), broadcastTitles);
        }
    }

    // ── Per-alert processing ──────────────────────────────────────────────────

    private NotifiedBatch processAlert(Alert alert, String keyword) {
        String fromDate = NotificationLog.findByAlert(alert.id)
                .stream()
                .map(log -> log.sentAt.toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(alert.createdAt.toLocalDate())
                .format(DATE_FMT);

        NewsSearchPage page = fetchResults(keyword, fromDate);
        if (page.results.isEmpty()) {
            return NotifiedBatch.empty();
        }

        List<NewsSearchResultItem> newResults = page.results.stream()
                .filter(r -> r.url != null && !NotificationLog.alreadyNotified(alert.id, r.url))
                .toList();

        if (newResults.isEmpty()) {
            LOG.debugf("All results already notified for alert id=%d keyword='%s'",
                    alert.id, keyword);
            return NotifiedBatch.empty();
        }

        LOG.infof("Sending email to '%s' for keyword='%s' (%d new results)",
                alert.user.email, keyword, newResults.size());

        sendEmail(alert, keyword, newResults);
        saveNotificationLogs(alert, newResults);

        return new NotifiedBatch(
                newResults.stream().map(r -> r.url).toList(),
                newResults.stream()
                          .map(r -> r.title != null && !r.title.isBlank() ? r.title : r.url)
                          .toList()
        );
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private void sendEmail(Alert alert, String keyword, List<NewsSearchResultItem> results) {
        String subject = String.format("NewsAlert: %d new result%s for '%s'",
                results.size(), results.size() == 1 ? "" : "s", keyword);

        String body = alertEmailTemplate
                .data("keyword", keyword)
                .data("results", results)
                .data("count", results.size())
                .render();

        mailer.send(Mail.withHtml(alert.user.email, subject, body));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void saveNotificationLogs(Alert alert, List<NewsSearchResultItem> results) {
        for (NewsSearchResultItem r : results) {
            NotificationLog log = new NotificationLog();
            log.alert = alert;
            log.resultUrl   = r.url;
            log.resultTitle = r.title != null ? r.title : "";
            log.persist();
        }
    }

    // ── Remote calls ──────────────────────────────────────────────────────────

    private NewsSearchPage fetchResults(String keyword, String fromDate) {
        try {
            return newsServiceClient.search(keyword, keyword, fromDate, 0, 100);
        } catch (Exception e) {
            LOG.warnf("Could not fetch results from news-service for keyword='%s': %s",
                    keyword, e.getMessage());
            return new NewsSearchPage();
        }
    }

    private void markNotifiedInNewsService(List<String> urls) {
        try {
            newsServiceInternalClient.markNotified(urls);
            LOG.debugf("Marked %d URL(s) as notified in news-service", urls.size());
        } catch (Exception e) {
            LOG.warnf("Failed to mark results as notified in news-service: %s", e.getMessage());
        }
    }

    // ── Value type ────────────────────────────────────────────────────────────

    private record NotifiedBatch(List<String> urls, List<String> titles) {
        static NotifiedBatch empty() {
            return new NotifiedBatch(List.of(), List.of());
        }
    }
}
