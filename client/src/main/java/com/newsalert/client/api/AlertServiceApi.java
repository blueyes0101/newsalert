package com.newsalert.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Thin OkHttp wrapper around the alert-service REST API.
 * All methods throw {@link ApiException} on HTTP errors.
 */
public class AlertServiceApi {

    private static final Logger LOG = LoggerFactory.getLogger(AlertServiceApi.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    private final OkHttpClient http;
    private final String baseUrl;

    public AlertServiceApi(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /** Registers a new account and returns the JWT token. */
    public String register(String email, String password) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        return extractToken(post("/api/auth/register", body.toString(), null));
    }

    /** Logs in and returns the JWT token. */
    public String login(String email, String password) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        return extractToken(post("/api/auth/login", body.toString(), null));
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    /** Creates a new keyword alert and returns its id. */
    public long createAlert(String keyword, String token) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("keyword", keyword);
        String json = post("/api/alerts", body.toString(), token);
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        return obj.get("id").getAsLong();
    }

    /** Returns all active alerts for the authenticated user. */
    public List<AlertDto> listAlerts(String token) throws ApiException {
        String json = get("/api/alerts", token);
        Type listType = new TypeToken<List<AlertDto>>() {}.getType();
        List<AlertDto> alerts = GSON.fromJson(json, listType);
        return alerts != null ? alerts : Collections.emptyList();
    }

    /** Deletes an alert by id. */
    public void deleteAlert(long id, String token) throws ApiException {
        delete("/api/alerts/" + id, token);
    }

    /** Toggles an alert's active state. */
    public AlertDto toggleAlert(long id, String token) throws ApiException {
        String json = put("/api/alerts/" + id + "/toggle", token);
        return GSON.fromJson(json, AlertDto.class);
    }

    // ── Crawler ───────────────────────────────────────────────────────────────

    /**
     * Fires POST {newsBaseUrl}/api/crawler/run to kick off an immediate crawl.
     *
     * @param newsBaseUrl base URL of the news-service (e.g. http://localhost:8080)
     */
    public void triggerCrawler(String newsBaseUrl, String token) throws ApiException {
        Request req = new Request.Builder()
                .url(newsBaseUrl + "/api/crawler/run")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create("", JSON))
                .build();
        execute(req);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Searches for news results matching {@code keyword} via the news-service.
     *
     * @param newsBaseUrl base URL of the news-service (e.g. http://localhost:8080)
     */
    public List<SearchResultDto> searchResults(String keyword, String newsBaseUrl, String token)
            throws ApiException {
        String url = newsBaseUrl
                + "/api/search?q=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                + "&page=0&size=20";
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        String json = execute(req);
        JsonObject page = GSON.fromJson(json, JsonObject.class);
        JsonArray arr = page.has("results") ? page.getAsJsonArray("results") : new JsonArray();
        Type listType = new TypeToken<List<SearchResultDto>>() {}.getType();
        List<SearchResultDto> results = GSON.fromJson(arr, listType);
        return results != null ? results : Collections.emptyList();
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String get(String path, String token) throws ApiException {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        return execute(req);
    }

    private String post(String path, String jsonBody, String token) throws ApiException {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(jsonBody, JSON));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return execute(builder.build());
    }

    private String put(String path, String token) throws ApiException {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .put(RequestBody.create("", JSON))
                .build();
        return execute(req);
    }

    private void delete(String path, String token) throws ApiException {
        Request req = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .delete()
                .build();
        execute(req);
    }

    private String execute(Request req) throws ApiException {
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                LOG.warn("HTTP {} {} — {}", resp.code(), req.url(), body);
                throw new ApiException(resp.code(), body);
            }
            return body;
        } catch (IOException e) {
            throw new ApiException(0, e.getMessage());
        }
    }

    private String extractToken(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        return obj.get("token").getAsString();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public static class SearchResultDto {
        public long   id;
        public String title;
        public String snippet;
        public String url;
        public String source;
        public String keyword;
        public String discoveredAt;

        public String getTitle()       { return title; }
        public String getUrl()         { return url; }
        public String getDiscoveredAt() { return discoveredAt; }
    }

    public static class AlertDto {
        public long id;
        public String keyword;
        public boolean active;
        public String createdAt;

        public long getId()       { return id; }
        public String getKeyword() { return keyword; }
        public boolean isActive()  { return active; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class ApiException extends Exception {
        public final int statusCode;

        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
