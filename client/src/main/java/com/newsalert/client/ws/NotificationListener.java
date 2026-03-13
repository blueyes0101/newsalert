package com.newsalert.client.ws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket client that connects to alert-service and fires
 * {@link NotificationHandler} on the calling thread (use
 * {@code Platform.runLater()} in the handler for JavaFX work).
 *
 * Reconnects automatically with exponential back-off (5 s → 10 s → 20 s … capped at 60 s).
 */
public class NotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListener.class);
    private static final Gson GSON = new Gson();
    private static final long BASE_DELAY_SECONDS = 5;
    private static final long MAX_DELAY_SECONDS  = 60;

    private final OkHttpClient http;
    private final String wsUrl;
    private final NotificationHandler handler;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private WebSocket webSocket;
    private long reconnectDelay = BASE_DELAY_SECONDS;

    public NotificationListener(String alertServiceUrl, NotificationHandler handler) {
        this.wsUrl   = alertServiceUrl.replace("http://", "ws://")
                                      .replace("https://", "wss://")
                       + "/ws/notifications";
        this.handler = handler;
        this.http    = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    /** Opens the WebSocket connection. Call once on application start. */
    public void start() {
        connect();
    }

    /** Closes the connection and stops the reconnect scheduler. */
    public void stop() {
        stopped.set(true);
        scheduler.shutdownNow();
        if (webSocket != null) webSocket.close(1000, "Application closing");
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void connect() {
        if (stopped.get()) return;
        LOG.info("Connecting to WebSocket: {}", wsUrl);
        Request req = new Request.Builder().url(wsUrl).build();
        webSocket = http.newWebSocket(req, new Listener());
    }

    private void scheduleReconnect() {
        if (stopped.get()) return;
        LOG.info("Reconnecting in {} s…", reconnectDelay);
        scheduler.schedule(this::connect, reconnectDelay, TimeUnit.SECONDS);
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_DELAY_SECONDS);
    }

    private class Listener extends WebSocketListener {

        @Override
        public void onOpen(@NotNull WebSocket ws, @NotNull Response response) {
            LOG.info("WebSocket connected to {}", wsUrl);
            reconnectDelay = BASE_DELAY_SECONDS;
        }

        @Override
        public void onMessage(@NotNull WebSocket ws, @NotNull String text) {
            LOG.debug("WS message: {}", text);
            try {
                JsonObject obj = GSON.fromJson(text, JsonObject.class);
                String keyword = obj.has("keyword") ? obj.get("keyword").getAsString() : "?";
                int    count   = obj.has("count")   ? obj.get("count").getAsInt()      : 0;
                handler.onNotification(keyword, count);
            } catch (Exception e) {
                LOG.warn("Could not parse WS message: {}", e.getMessage());
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket ws, @NotNull Throwable t,
                              Response response) {
            LOG.warn("WebSocket error: {}", t.getMessage());
            scheduleReconnect();
        }

        @Override
        public void onClosed(@NotNull WebSocket ws, int code, @NotNull String reason) {
            if (!stopped.get()) {
                LOG.info("WebSocket closed ({}): {} – reconnecting", code, reason);
                scheduleReconnect();
            }
        }
    }
}
