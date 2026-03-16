package com.newsalert.client.ui;

import com.newsalert.client.api.AlertServiceApi;
import com.newsalert.client.api.AlertServiceApi.AlertDto;
import com.newsalert.client.config.AppConfig;
import com.newsalert.client.config.ConfigManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls KeywordManager.fxml.
 * Lists active alerts, allows adding new ones and deleting / toggling existing ones.
 */
public class KeywordManagerController {

    private static final Logger LOG = LoggerFactory.getLogger(KeywordManagerController.class);

    @FXML private TableView<AlertDto>        alertTable;
    @FXML private TableColumn<AlertDto, Long>    colId;
    @FXML private TableColumn<AlertDto, String>  colKeyword;
    @FXML private TableColumn<AlertDto, Boolean> colActive;
    @FXML private TextField keywordInput;
    @FXML private Label     statusLabel;

    private final ObservableList<AlertDto> items = FXCollections.observableArrayList();
    private AlertServiceApi api;
    private AppConfig config;

    @FXML
    public void initialize() {
        config = ConfigManager.load();
        api    = new AlertServiceApi(config.alertServiceUrl);

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colKeyword.setCellValueFactory(new PropertyValueFactory<>("keyword"));
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));

        alertTable.setItems(items);
        refreshAlerts();
    }

    @FXML
    void onAdd() {
        String keyword = keywordInput.getText().trim();
        if (keyword.isBlank()) {
            statusLabel.setText("Enter a keyword first.");
            return;
        }
        statusLabel.setText("Adding…");

        new Thread(() -> {
            try {
                api.createAlert(keyword, config.jwtToken);
                Platform.runLater(() -> {
                    keywordInput.clear();
                    statusLabel.setText("Added: " + keyword);
                    refreshAlerts();
                });
                triggerCrawlerAsync(keyword);
            } catch (AlertServiceApi.ApiException ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        }, "kw-add").start();
    }

    @FXML
    void onDelete() {
        AlertDto selected = alertTable.getSelectionModel().getSelectedItem();
        if (selected == null) { statusLabel.setText("Select an alert first."); return; }

        new Thread(() -> {
            try {
                api.deleteAlert(selected.id, config.jwtToken);
                Platform.runLater(() -> {
                    statusLabel.setText("Deleted: " + selected.keyword);
                    refreshAlerts();
                });
            } catch (AlertServiceApi.ApiException ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        }, "kw-delete").start();
    }

    @FXML
    void onToggle() {
        AlertDto selected = alertTable.getSelectionModel().getSelectedItem();
        if (selected == null) { statusLabel.setText("Select an alert first."); return; }

        new Thread(() -> {
            try {
                AlertDto updated = api.toggleAlert(selected.id, config.jwtToken);
                Platform.runLater(() -> {
                    statusLabel.setText(updated.keyword + " is now " +
                            (updated.active ? "active" : "paused"));
                    refreshAlerts();
                });
            } catch (AlertServiceApi.ApiException ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        }, "kw-toggle").start();
    }

    @FXML
    void onRefresh() {
        refreshAlerts();
    }

    private void triggerCrawlerAsync(String keyword) {
        new Thread(() -> {
            try {
                api.triggerCrawler(config.newsServiceUrl, config.jwtToken);
                LOG.info("Crawler triggered for new keyword: {}", keyword);
            } catch (AlertServiceApi.ApiException ex) {
                LOG.warn("Crawler trigger failed for keyword '{}': {}", keyword, ex.getMessage());
            }
        }, "kw-crawler").start();
    }

    private void refreshAlerts() {
        new Thread(() -> {
            try {
                var alerts = api.listAlerts(config.jwtToken);
                Platform.runLater(() -> {
                    items.setAll(alerts);
                    statusLabel.setText(alerts.size() + " alert(s)");
                });
            } catch (AlertServiceApi.ApiException ex) {
                Platform.runLater(() -> statusLabel.setText("Could not load alerts: " + ex.getMessage()));
            }
        }, "kw-refresh").start();
    }
}
