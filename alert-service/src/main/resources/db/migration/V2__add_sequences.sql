-- ============================================================
-- V2__add_sequences.sql
-- Hibernate 6 (Quarkus 3) resolves GenerationType.AUTO to
-- SEQUENCE and names each sequence <table>_seq.  These must
-- exist in addition to the BIGSERIAL implicit sequences.
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS users_seq              START 1 INCREMENT 50;
CREATE SEQUENCE IF NOT EXISTS alerts_seq             START 1 INCREMENT 50;
CREATE SEQUENCE IF NOT EXISTS notification_logs_seq  START 1 INCREMENT 50;
