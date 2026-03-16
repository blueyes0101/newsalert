package com.newsalert.client.ui;

import com.newsalert.client.config.AppConfig;
import com.newsalert.client.config.ConfigManager;
import com.newsalert.client.crawler.CrawlScheduler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Controls Settings.fxml.
 * Allows editing server URLs, and provides Logout.
 */
public class SettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);

    @FXML private TextField emailField;
    @FXML private TextField alertServiceUrlField;
    @FXML private TextField newsServiceUrlField;
    @FXML private TextField crawlIntervalField;
    @FXML private Label     statusLabel;

    private AppConfig config;

    @FXML
    public void initialize() {
        config = ConfigManager.load();
        emailField.setText(config.email);
        alertServiceUrlField.setText(config.alertServiceUrl);
        newsServiceUrlField.setText(config.newsServiceUrl);
        crawlIntervalField.setText(String.valueOf(config.crawlIntervalMinutes));
    }

    @FXML
    void onSave() {
        String alertUrl  = alertServiceUrlField.getText().trim();
        String newsUrl   = newsServiceUrlField.getText().trim();
        String intervalStr = crawlIntervalField.getText().trim();

        if (alertUrl.isBlank() || newsUrl.isBlank()) {
            statusLabel.setText("URLs must not be empty.");
            return;
        }

        int interval;
        try {
            interval = Integer.parseInt(intervalStr);
        } catch (NumberFormatException e) {
            statusLabel.setText("Crawl interval must be a number.");
            return;
        }
        if (interval < 1 || interval > 60) {
            statusLabel.setText("Crawl interval must be between 1 and 60.");
            return;
        }

        config.alertServiceUrl     = alertUrl;
        config.newsServiceUrl      = newsUrl;
        config.crawlIntervalMinutes = interval;
        ConfigManager.save(config);

        CrawlScheduler.getInstance().restart(config.newsServiceUrl, config.jwtToken, interval);

        statusLabel.setText("Settings saved.");
        LOG.info("Settings saved — alertServiceUrl={}, newsServiceUrl={}, crawlInterval={}min",
                alertUrl, newsUrl, interval);
    }

    @FXML
    void onChangePassword() {
        statusLabel.setText("Change password is not available in this version.");
    }

    @FXML
    void onLogout() {
        // Preserve server URLs, clear credentials
        AppConfig fresh = new AppConfig();
        fresh.alertServiceUrl = config.alertServiceUrl;
        fresh.newsServiceUrl  = config.newsServiceUrl;
        ConfigManager.save(fresh);
        LOG.info("User logged out");

        // Close every open window except the one we are about to open
        List.copyOf(Window.getWindows()).forEach(w -> {
            if (w instanceof Stage s) s.close();
        });

        // Reopen Setup Wizard
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/SetupWizard.fxml"));
            Parent root = loader.load();
            Stage wizard = new Stage();
            wizard.setTitle("NewsAlert \u2013 Setup");
            wizard.setResizable(false);
            wizard.initModality(Modality.APPLICATION_MODAL);
            wizard.setScene(new Scene(root, 480, 400));
            wizard.show();
        } catch (IOException e) {
            LOG.error("Could not open SetupWizard after logout: {}", e.getMessage());
        }
    }
}
