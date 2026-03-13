package com.newsalert.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads and writes {@link AppConfig} to {@code ~/.newsalert/config.json}.
 */
public class ConfigManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR  = Paths.get(System.getProperty("user.home"), ".newsalert");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private static AppConfig instance;

    private ConfigManager() {}

    /** True when the config file exists (i.e. setup wizard has been completed). */
    public static boolean isConfigured() {
        return Files.exists(CONFIG_FILE);
    }

    /**
     * Loads config from disk. If the file is absent or malformed, returns a
     * fresh default {@link AppConfig} without writing anything to disk.
     */
    public static AppConfig load() {
        if (instance != null) return instance;

        if (!Files.exists(CONFIG_FILE)) {
            instance = new AppConfig();
            return instance;
        }
        try {
            String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            instance = GSON.fromJson(json, AppConfig.class);
            if (instance == null) instance = new AppConfig();
        } catch (IOException e) {
            LOG.warn("Could not read config file, using defaults: {}", e.getMessage());
            instance = new AppConfig();
        }
        return instance;
    }

    /** Persists the current config to disk, creating the directory if needed. */
    public static void save(AppConfig config) {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8);
            instance = config;
            LOG.debug("Config saved to {}", CONFIG_FILE);
        } catch (IOException e) {
            LOG.error("Failed to save config: {}", e.getMessage());
        }
    }
}
