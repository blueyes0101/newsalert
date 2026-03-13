package com.newsalert.alert.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Application-scoped CDI bean that owns the live WebSocket session registry
 * and broadcasts notification messages to all connected JavaFX clients.
 *
 * Thread-safe: {@link CopyOnWriteArraySet} allows concurrent reads while
 * connect/disconnect events modify the set without locking.
 */
@ApplicationScoped
public class WebSocketBroadcaster {

    private static final Logger LOG = Logger.getLogger(WebSocketBroadcaster.class);

    private final Set<Session> sessions = new CopyOnWriteArraySet<>();

    @Inject
    ObjectMapper objectMapper;

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public void register(Session session) {
        sessions.add(session);
        LOG.infof("WebSocket client connected: %s  (total=%d)",
                session.getId(), sessions.size());
    }

    public void unregister(Session session) {
        sessions.remove(session);
        LOG.infof("WebSocket client disconnected: %s  (total=%d)",
                session.getId(), sessions.size());
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    /**
     * Serialises the payload to JSON and sends it asynchronously to every
     * open session. Closed / broken sessions are silently removed.
     *
     * @param keyword keyword that produced new results
     * @param count   total number of new results across all user alerts
     * @param titles  up to 10 distinct article titles (may be empty)
     */
    public void broadcast(String keyword, int count, List<String> titles) {
        if (sessions.isEmpty()) {
            LOG.debugf("No WS clients connected – skipping broadcast for keyword='%s'", keyword);
            return;
        }

        String json = toJson(keyword, count, titles);
        if (json == null) return;

        LOG.infof("Broadcasting to %d WS client(s): keyword='%s' count=%d",
                sessions.size(), keyword, count);

        for (Session session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            session.getAsyncRemote().sendText(json, result -> {
                if (!result.isOK()) {
                    LOG.warnf("WS send failed for session %s: %s",
                            session.getId(), result.getException().getMessage());
                    sessions.remove(session);
                }
            });
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toJson(String keyword, int count, List<String> titles) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("keyword", keyword,
                           "count",   count,
                           "titles",  titles));
        } catch (JsonProcessingException e) {
            LOG.errorf("Failed to serialize WS broadcast payload: %s", e.getMessage());
            return null;
        }
    }
}
