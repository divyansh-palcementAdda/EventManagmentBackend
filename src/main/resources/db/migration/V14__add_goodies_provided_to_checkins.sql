-- ============================================================
-- V14__add_goodies_provided_to_checkins.sql
-- Add goodies provided tracking to check_ins table
-- ============================================================

ALTER TABLE check_ins
    ADD COLUMN goodies_provided BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN goodies_provided_at DATETIME(6) NULL,
    ADD COLUMN goodies_provided_by_id VARCHAR(36) NULL,
    ADD COLUMN goodies_provided_by_name VARCHAR(150) NULL;

CREATE INDEX idx_ci_goodies ON check_ins (event_id, goodies_provided);
