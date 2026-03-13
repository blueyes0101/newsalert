package com.newsalert.client.ui;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Controls NotificationPopup.fxml.
 * Factory method {@link #show} creates and positions the popup in the
 * bottom-right corner of the primary screen; it auto-closes after 8 seconds.
 */
public class NotificationPopupController {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationPopupController.class);

    private static final double POPUP_WIDTH  = 320;
    private static final double POPUP_HEIGHT = 110;
    private static final double MARGIN       = 16;
    private static final double AUTO_CLOSE_SECONDS = 8;

    @FXML private Label messageLabel;

    private Stage stage;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates and shows a popup for the given keyword / count pair.
     * Must be called on the JavaFX application thread.
     */
    public static void show(String keyword, int count) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    NotificationPopupController.class.getResource("/fxml/NotificationPopup.fxml"));
            Parent root = loader.load();
            NotificationPopupController ctrl = loader.getController();

            Stage popupStage = new Stage(StageStyle.UNDECORATED);
            popupStage.setAlwaysOnTop(true);
            popupStage.setScene(new Scene(root, POPUP_WIDTH, POPUP_HEIGHT));

            // Position: bottom-right corner
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            popupStage.setX(screen.getMaxX() - POPUP_WIDTH  - MARGIN);
            popupStage.setY(screen.getMaxY() - POPUP_HEIGHT - MARGIN);

            ctrl.stage = popupStage;
            ctrl.messageLabel.setText(
                    "\uD83D\uDD14 " + count + " new result" + (count == 1 ? "" : "s") +
                    " for \u2018" + keyword + "\u2019");

            popupStage.show();
            ctrl.startAutoClose();

        } catch (IOException e) {
            LOG.error("Could not load NotificationPopup.fxml: {}", e.getMessage());
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    void onView() {
        // Placeholder: a future prompt will open the Recent Results window
        LOG.info("View button clicked in notification popup");
        close();
    }

    @FXML
    void onClose() {
        close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void startAutoClose() {
        PauseTransition pause = new PauseTransition(Duration.seconds(AUTO_CLOSE_SECONDS));
        pause.setOnFinished(e -> close());
        pause.play();
    }

    private void close() {
        if (stage != null) stage.close();
    }
}
