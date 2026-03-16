package com.newsalert.client.config;

/**
 * Serialized to/from ~/.newsalert/config.json via Gson.
 */
public class AppConfig {

    public String email                = "";
    public String jwtToken             = "";
    public String alertServiceUrl      = "http://localhost:8081";
    public String newsServiceUrl       = "http://localhost:8080";
    public int    crawlIntervalMinutes = 15;
}
