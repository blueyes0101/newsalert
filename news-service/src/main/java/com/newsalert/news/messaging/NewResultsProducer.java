package com.newsalert.news.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Publishes {@link NewResultsEvent} JSON payloads to the Kafka "new-results" topic.
 */
@ApplicationScoped
public class NewResultsProducer {

    private static final Logger LOG = Logger.getLogger(NewResultsProducer.class);

    @Inject
    @Channel("new-results")
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Sends a crawl-cycle summary to Kafka only when at least one new result was saved.
     *
     * @param keyword keyword that was crawled
     * @param count   number of newly persisted results
     */
    public void sendNewResults(String keyword, int count) {
        if (count == 0) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(new NewResultsEvent(keyword, count));
            emitter.send(json);
            LOG.debugf("Kafka → new-results: keyword='%s' count=%d", keyword, count);
        } catch (JsonProcessingException e) {
            LOG.errorf("Failed to serialize NewResultsEvent for keyword='%s': %s",
                    keyword, e.getMessage());
        }
    }
}
