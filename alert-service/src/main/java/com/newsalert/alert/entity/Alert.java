package com.newsalert.alert.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alerts")
@Indexed
public class Alert extends PanacheEntity {

    @Column(name = "keyword", nullable = false)
    @FullTextField
    @KeywordField
    public String keyword;

    @Column(name = "active", nullable = false)
    @KeywordField
    public boolean active = true;

    @Column(name = "created_at", nullable = false)
    @GenericField(sortable = Sortable.YES)
    public LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @IndexedEmbedded
    public User user;

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<NotificationLog> notifications = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ── Panache finders ───────────────────────────────────────────────────────

    public static List<Alert> findActiveByUser(Long userId) {
        return list("user.id = ?1 and active = true", userId);
    }

    public static List<Alert> findAllActive() {
        return list("active", true);
    }

    public static List<Alert> findActiveByKeyword(String keyword) {
        return list("keyword = ?1 and active = true", keyword);
    }
}
