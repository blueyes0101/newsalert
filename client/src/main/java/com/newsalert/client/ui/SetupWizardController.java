package com.newsalert.client.ui;

import com.newsalert.client.api.AlertServiceApi;
import com.newsalert.client.config.AppConfig;
import com.newsalert.client.config.ConfigManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls SetupWizard.fxml.
 *
 * Steps:
 *   0 – Welcome
 *   1 – Register (email + password)
 *   2 – Add first keyword
 *   3 – "System is ready!"
 */
public class SetupWizardController {

    private static final Logger LOG = LoggerFactory.getLogger(SetupWizardController.class);

    // ── Step panes ────────────────────────────────────────────────────────────
    @FXML private VBox stepWelcome;
    @FXML private VBox stepRegister;
    @FXML private VBox stepKeyword;
    @FXML private VBox stepDone;

    // ── Registration fields ───────────────────────────────────────────────────
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmField;
    @FXML private Label         registerError;

    // ── Keyword fields ────────────────────────────────────────────────────────
    @FXML private TextField keywordField;
    @FXML private Label     keywordError;

    // ── Navigation ────────────────────────────────────────────────────────────
    @FXML private Button nextBtn;
    @FXML private Button backBtn;

    private int currentStep = 0;
    private AppConfig config;
    private AlertServiceApi api;

    /** Called by FXMLLoader. */
    @FXML
    public void initialize() {
        config = ConfigManager.load();
        api    = new AlertServiceApi(config.alertServiceUrl);
        showStep(0);
    }

    // ── Navigation handlers ───────────────────────────────────────────────────

    @FXML
    void onNext() {
        switch (currentStep) {
            case 0 -> showStep(1);
            case 1 -> handleRegister();
            case 2 -> handleAddKeyword();
            case 3 -> finish();
        }
    }

    @FXML
    void onBack() {
        if (currentStep > 0) showStep(currentStep - 1);
    }

    // ── Step logic ────────────────────────────────────────────────────────────

    private void handleRegister() {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmField.getText();

        if (email.isBlank() || password.isBlank()) {
            registerError.setText("Email and password are required.");
            return;
        }
        if (password.length() < 8) {
            registerError.setText("Password must be at least 8 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            registerError.setText("Passwords do not match.");
            return;
        }

        nextBtn.setDisable(true);
        registerError.setText("Creating account…");

        new Thread(() -> {
            try {
                String token = api.register(email, password);
                config.email    = email;
                config.jwtToken = token;
                ConfigManager.save(config);
                Platform.runLater(() -> showStep(2));
            } catch (AlertServiceApi.ApiException ex) {
                String msg = ex.statusCode == 409
                        ? "Email already registered."
                        : "Registration failed: " + ex.getMessage();
                Platform.runLater(() -> {
                    registerError.setText(msg);
                    nextBtn.setDisable(false);
                });
            }
        }, "setup-register").start();
    }

    private void handleAddKeyword() {
        String keyword = keywordField.getText().trim();
        if (keyword.isBlank()) {
            keywordError.setText("Please enter a keyword.");
            return;
        }
        if (keyword.length() < 2) {
            keywordError.setText("Keyword must be at least 2 characters.");
            return;
        }

        nextBtn.setDisable(true);
        keywordError.setText("Saving…");

        new Thread(() -> {
            try {
                api.createAlert(keyword, config.jwtToken);
                Platform.runLater(() -> showStep(3));
            } catch (AlertServiceApi.ApiException ex) {
                Platform.runLater(() -> {
                    keywordError.setText("Could not save keyword: " + ex.getMessage());
                    nextBtn.setDisable(false);
                });
            }
        }, "setup-keyword").start();
    }

    private void finish() {
        Stage stage = (Stage) nextBtn.getScene().getWindow();
        stage.close();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void showStep(int step) {
        currentStep = step;

        stepWelcome.setVisible(step == 0);  stepWelcome.setManaged(step == 0);
        stepRegister.setVisible(step == 1); stepRegister.setManaged(step == 1);
        stepKeyword.setVisible(step == 2);  stepKeyword.setManaged(step == 2);
        stepDone.setVisible(step == 3);     stepDone.setManaged(step == 3);

        backBtn.setDisable(step == 0 || step == 3);
        nextBtn.setDisable(false);
        nextBtn.setText(step == 3 ? "Finish" : "Next →");

        // Clear error labels when navigating
        registerError.setText("");
        keywordError.setText("");
    }
}
