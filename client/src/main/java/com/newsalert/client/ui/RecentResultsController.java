package com.newsalert.client.ui;

import com.newsalert.client.api.AlertServiceApi;
import com.newsalert.client.api.AlertServiceApi.AlertDto;
import com.newsalert.client.api.AlertServiceApi.SearchResultDto;
import com.newsalert.client.config.AppConfig;
import com.newsalert.client.config.ConfigManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls RecentResults.fxml.
 * Fetches search results from the news-service for every active keyword alert.
 */
public class RecentResultsController {

    private static final Logger LOG = LoggerFactory.getLogger(RecentResultsController.class);

    @FXML private TableView<SearchResultDto>            resultsTable;
    @FXML private TableColumn<SearchResultDto, String>  colTitle;
    @FXML private TableColumn<SearchResultDto, String>  colSource;
    @FXML private TableColumn<SearchResultDto, String>  colDiscoveredAt;
    @FXML private Label statusLabel;

    private final ObservableList<SearchResultDto> items = FXCollections.observableArrayList();
    private AlertServiceApi api;
    private AppConfig config;

    @FXML
    public void initialize() {
        config = ConfigManager.load();
        api    = new AlertServiceApi(config.alertServiceUrl);

        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDiscoveredAt.setCellValueFactory(new PropertyValueFactory<>("discoveredAt"));

        colSource.setCellValueFactory(new PropertyValueFactory<>("url"));
        colSource.setCellFactory(col -> new TableCell<>() {
            private final Text link = new Text();
            {
                link.setStyle("-fx-fill: #1a73e8; -fx-underline: true; -fx-cursor: hand;");
                link.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && !link.getText().isBlank()) {
                        openInBrowser(link.getText());
                    }
                });
                setGraphic(link);
            }

            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                link.setText(empty || url == null ? "" : url);
            }
        });

        resultsTable.setItems(items);
        refresh();
    }

    @FXML
    void onRefresh() {
        refresh();
    }

    private void openInBrowser(String url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            LOG.warn("Desktop browse not supported; cannot open URL: {}", url);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            LOG.warn("Failed to open URL '{}': {}", url, ex.getMessage());
        }
    }

    private void refresh() {
        statusLabel.setText("Loading…");

        new Thread(() -> {
            try {
                List<AlertDto> alerts = api.listAlerts(config.jwtToken);
                List<AlertDto> active = alerts.stream()
                        .filter(a -> a.active)
                        .toList();

                if (active.isEmpty()) {
                    Platform.runLater(() -> {
                        items.clear();
                        statusLabel.setText("No active keywords. Add keywords in My Keywords.");
                    });
                    return;
                }

                List<SearchResultDto> allResults = new ArrayList<>();
                for (AlertDto alert : active) {
                    try {
                        List<SearchResultDto> results =
                                api.searchResults(alert.keyword, config.newsServiceUrl, config.jwtToken);
                        allResults.addAll(results);
                    } catch (AlertServiceApi.ApiException ex) {
                        LOG.warn("Search failed for keyword '{}': {}", alert.keyword, ex.getMessage());
                    }
                }

                List<SearchResultDto> snapshot = allResults;
                Platform.runLater(() -> {
                    items.setAll(snapshot);
                    statusLabel.setText(snapshot.size() + " result(s) across " + active.size() + " keyword(s)");
                });

            } catch (AlertServiceApi.ApiException ex) {
                Platform.runLater(() -> statusLabel.setText("Could not load alerts: " + ex.getMessage()));
            }
        }, "results-refresh").start();
    }
}
