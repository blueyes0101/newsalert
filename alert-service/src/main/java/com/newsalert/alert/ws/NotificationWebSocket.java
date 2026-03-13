package com.newsalert.alert.ws;

import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

/**
 * WebSocket endpoint at {@code /ws/notifications}.
 *
 * Quarkus instantiates this class per connection but resolves the
 * {@link WebSocketBroadcaster} injection from the CDI application context,
 * so all endpoint instances share the same session registry.
 *
 * The endpoint is intentionally thin: it only manages session
 * registration. All business logic lives in {@link WebSocketBroadcaster}.
 */
@ServerEndpoint("/ws/notifications")
public class NotificationWebSocket {

    private static final Logger LOG = Logger.getLogger(NotificationWebSocket.class);

    @Inject
    WebSocketBroadcaster broadcaster;

    @OnOpen
    public void onOpen(Session session) {
        broadcaster.register(session);
        // Immediately acknowledge the connection
        session.getAsyncRemote().sendText("{\"type\":\"connected\",\"message\":\"NewsAlert WS ready\"}");
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        broadcaster.unregister(session);
        LOG.debugf("WS closed [%s]: %s", session.getId(), reason.getReasonPhrase());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOG.warnf("WS error [%s]: %s", session.getId(), throwable.getMessage());
        broadcaster.unregister(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // Client-to-server messages are not used in this version (one-way push).
        LOG.debugf("WS inbound (ignored) [%s]: %s", session.getId(), message);
    }
}
