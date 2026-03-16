package com.newsalert.client.tray;

import com.newsalert.client.ui.KeywordManagerController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Manages the AWT system tray icon and its context menu.
 *
 * JavaFX windows opened from tray menu items are created on the
 * JavaFX application thread via {@code Platform.runLater()}.
 */
public class TrayManager {

    private static final Logger LOG = LoggerFactory.getLogger(TrayManager.class);

    private TrayIcon trayIcon;

    /**
     * Installs the tray icon. Returns {@code false} if the desktop does not
     * support a system tray (headless servers, some Linux DEs without a tray).
     */
    public boolean install() {
        if (!SystemTray.isSupported()) {
            LOG.warn("System tray not supported on this platform");
            return false;
        }

        Image icon = createIcon();
        PopupMenu menu = buildMenu();

        trayIcon = new TrayIcon(icon, "NewsAlert", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(this::openKeywordManager));

        try {
            SystemTray.getSystemTray().add(trayIcon);
            LOG.info("System tray icon installed");
            return true;
        } catch (AWTException e) {
            LOG.error("Failed to add tray icon: {}", e.getMessage());
            return false;
        }
    }

    /** Removes the icon from the tray (called on application exit). */
    public void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    /** Shows a balloon notification (Windows) or tooltip (Linux). */
    public void showBalloon(String caption, String text) {
        if (trayIcon != null) {
            trayIcon.displayMessage(caption, text, TrayIcon.MessageType.INFO);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem keywords = new MenuItem("My Keywords");
        keywords.addActionListener(e -> Platform.runLater(this::openKeywordManager));

        MenuItem results = new MenuItem("Recent Results");
        results.addActionListener(e -> Platform.runLater(this::openRecentResults));

        MenuItem settings = new MenuItem("Settings");
        settings.addActionListener(e -> Platform.runLater(this::openSettings));

        MenuItem separator = new MenuItem("-");
        separator.setEnabled(false);

        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> {
            remove();
            Platform.exit();
            System.exit(0);
        });

        menu.add(keywords);
        menu.add(results);
        menu.add(settings);
        menu.addSeparator();
        menu.add(exit);

        return menu;
    }

    private void openKeywordManager() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/KeywordManager.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("NewsAlert – My Keywords");
            stage.setScene(new Scene(root, 600, 420));
            stage.show();
        } catch (IOException e) {
            LOG.error("Could not open KeywordManager: {}", e.getMessage());
        }
    }

    private void openRecentResults() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/RecentResults.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("NewsAlert – Recent Results");
            stage.setScene(new Scene(root, 800, 500));
            stage.show();
        } catch (IOException e) {
            LOG.error("Could not open RecentResults: {}", e.getMessage());
        }
    }

    private void openSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/Settings.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("NewsAlert \u2013 Settings");
            stage.setResizable(false);
            stage.setScene(new Scene(root, 460, 340));
            stage.show();
        } catch (IOException e) {
            LOG.error("Could not open Settings: {}", e.getMessage());
        }
    }

    /**
     * Programmatically generates a 16×16 blue circle as the tray icon.
     * Replace with a real PNG resource in production.
     */
    private Image createIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(26, 115, 232));   // Google-blue
        g.fillOval(0, 0, 15, 15);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("N", 3, 12);
        g.dispose();
        return img;
    }
}
