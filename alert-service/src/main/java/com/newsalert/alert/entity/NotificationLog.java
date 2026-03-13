package com.newsalert.alert.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "notification_logs")
public class NotificationLog extends PanacheEntity {

    @Column(name = "result_url", length = 2048)
    public String resultUrl;

    @Column(name = "result_title", length = 512)
    public String resultTitle;

    @Column(name = "sent_at", nullable = false)
    public LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    public Alert alert;

    @PrePersist
    void prePersist() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    // ── Panache finders ───────────────────────────────────────────────────────

    public static List<NotificationLog> findByAlert(Long alertId) {
        return list("alert.id", alertId);
    }

    public static boolean alreadyNotified(Long alertId, String url) {
        return count("alert.id = ?1 and resultUrl = ?2", alertId, url) > 0;
    }
}
