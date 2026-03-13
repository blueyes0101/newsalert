-- ============================================================
-- V1__init.sql  –  NewsAlert initial schema
-- ============================================================

-- ── users ────────────────────────────────────────────────────
CREATE TABLE users (
    id            BIGSERIAL     PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

-- ── alerts ───────────────────────────────────────────────────
CREATE TABLE alerts (
    id         BIGSERIAL    PRIMARY KEY,
    keyword    VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_alerts_user_id ON alerts (user_id);
CREATE INDEX idx_alerts_active  ON alerts (active);

-- ── notification_logs ─────────────────────────────────────────
CREATE TABLE notification_logs (
    id           BIGSERIAL     PRIMARY KEY,
    result_url   VARCHAR(2048),
    result_title VARCHAR(512),
    sent_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    alert_id     BIGINT        NOT NULL REFERENCES alerts (id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_logs_alert_id ON notification_logs (alert_id);
CREATE INDEX idx_notification_logs_sent_at  ON notification_logs (sent_at);
