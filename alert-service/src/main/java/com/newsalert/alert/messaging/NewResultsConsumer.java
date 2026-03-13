package com.newsalert.alert.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsalert.alert.dto.NewResultEvent;
import com.newsalert.alert.service.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Kafka consumer for the "new-results" topic.
 *
 * Each message is a JSON string produced by news-service's {@code NewResultsProducer}:
 * <pre>{"keyword":"Iran","count":3,"timestamp":"2024-..."}</pre>
 *
 * Failures are caught and logged so a bad message does not stall the topic.
 */
@ApplicationScoped
public class NewResultsConsumer {

    private static final Logger LOG = Logger.getLogger(NewResultsConsumer.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NotificationService notificationService;

    @Incoming("new-results")
    public void onNewResults(String message) {
        LOG.debugf("Kafka ← new-results: %s", message);

        NewResultEvent event;
        try {
            event = objectMapper.readValue(message, NewResultEvent.class);
        } catch (Exception e) {
            LOG.errorf("Failed to parse new-results Kafka message: %s | payload: %s",
                    e.getMessage(), message);
            return;
        }

        if (event.keyword == null || event.keyword.isBlank()) {
            LOG.warn("Received new-results event with blank keyword, skipping");
            return;
        }

        LOG.infof("New results event: keyword='%s' count=%d", event.keyword, event.count);

        try {
            notificationService.processKeywordEvent(event.keyword);
        } catch (Exception e) {
            LOG.errorf("Unhandled error processing event for keyword='%s': %s",
                    event.keyword, e.getMessage());
        }
    }
}
