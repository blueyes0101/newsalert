package com.newsalert.alert.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {

    @Column(name = "email", nullable = false, unique = true)
    @FullTextField
    @KeywordField
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<Alert> alerts = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ── Panache finders ───────────────────────────────────────────────────────

    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }

    public static boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }
}
