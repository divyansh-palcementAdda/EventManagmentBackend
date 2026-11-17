-- ============================================================
-- V13__add_unique_code_to_event_passes.sql
-- Add globally unique middle alphanumeric code column to event_passes
-- ============================================================

ALTER TABLE event_passes ADD COLUMN unique_code VARCHAR(16) NULL AFTER pass_number;

CREATE UNIQUE INDEX idx_ep_unique_code ON event_passes (unique_code);
