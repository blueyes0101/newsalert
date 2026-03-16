package com.newsalert.client;

import com.newsalert.client.config.AppConfig;
import com.newsalert.client.config.ConfigManager;
import com.newsalert.client.crawler.CrawlScheduler;
import com.newsalert.client.tray.TrayManager;
import com.newsalert.client.ui.NotificationPopupController;
import com.newsalert.client.ws.NotificationListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JavaFX entry point for NewsAlert.
 *
 * Startup flow:
 *   1. Check ~/.newsalert/config.json
 *   2a. Missing → open SetupWizard (modal); when closed → go to step 3
 *   2b. Present  → skip wizard
 *   3. Install system tray icon
 *   4. Start WebSocket notification listener
 */
public class MainApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);

    private TrayManager trayManager;
    private NotificationListener wsListener;

    @Override
    public void start(Stage primaryStage) {
        // Keep JavaFX alive after all windows close (tray keeps the app running)
        Platform.setImplicitExit(false);

        if (!ConfigManager.isConfigured()) {
            showSetupWizard(primaryStage, this::onSetupComplete);
        } else {
            onSetupComplete();
        }
    }

    @Override
    public void stop() {
        if (wsListener  != null) wsListener.stop();
        if (trayManager != null) trayManager.remove();
        CrawlScheduler.getInstance().stop();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void showSetupWizard(Stage owner, Runnable onComplete) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/SetupWizard.fxml"));
            Parent root = loader.load();

            Stage wizard = new Stage();
            wizard.setTitle("NewsAlert – Setup");
            wizard.initModality(Modality.APPLICATION_MODAL);
            wizard.initOwner(owner);
            wizard.setResizable(false);
            wizard.setScene(new Scene(root, 480, 400));
            wizard.setOnHidden(e -> onComplete.run());
            wizard.show();

        } catch (IOException e) {
            LOG.error("Could not open SetupWizard.fxml: {}", e.getMessage());
        }
    }

    private void onSetupComplete() {
        AppConfig config = ConfigManager.load();

        // System tray
        trayManager = new TrayManager();
        if (!trayManager.install()) {
            LOG.warn("System tray unavailable – popup notifications only");
        }

        // WebSocket listener: push popup to JavaFX thread
        wsListener = new NotificationListener(
                config.alertServiceUrl,
                (keyword, count) -> Platform.runLater(
                        () -> NotificationPopupController.show(keyword, count)));
        wsListener.start();

        // Periodic crawler
        CrawlScheduler.getInstance().start(
                config.newsServiceUrl, config.jwtToken, config.crawlIntervalMinutes);

        LOG.info("NewsAlert is running");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
