-- ============================================================
-- V11__add_event_category_id_to_pass_allotments.sql
-- Add event_category_id foreign key column to pass_allotments
-- ============================================================

ALTER TABLE pass_allotments ADD COLUMN event_category_id BINARY(16) NULL AFTER category_id;
ALTER TABLE pass_allotments ADD CONSTRAINT fk_pa_event_category FOREIGN KEY (event_category_id) REFERENCES event_pass_categories (id) ON DELETE SET NULL;
CREATE INDEX idx_pa_event_category ON pass_allotments (event_category_id);
