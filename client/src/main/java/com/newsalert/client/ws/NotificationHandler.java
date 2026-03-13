package com.newsalert.client.ws;

/** Callback interface bridging WebSocket → JavaFX thread. */
@FunctionalInterface
public interface NotificationHandler {
    void onNotification(String keyword, int count);
}
