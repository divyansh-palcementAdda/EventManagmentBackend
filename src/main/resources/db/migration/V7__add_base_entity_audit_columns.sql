-- ============================================================
-- V7__add_base_entity_audit_columns.sql
-- Synchronize audit columns with BaseEntity for dynamic category & form builder tables
-- ============================================================

ALTER TABLE pass_categories
    ADD COLUMN created_by_id VARCHAR(36) NULL,
    ADD COLUMN created_by_name VARCHAR(150) NULL,
    ADD COLUMN updated_by_id VARCHAR(36) NULL,
    ADD COLUMN updated_by_name VARCHAR(150) NULL;

ALTER TABLE event_pass_categories
    ADD COLUMN created_by_id VARCHAR(36) NULL,
    ADD COLUMN created_by_name VARCHAR(150) NULL,
    ADD COLUMN updated_by_id VARCHAR(36) NULL,
    ADD COLUMN updated_by_name VARCHAR(150) NULL;

ALTER TABLE registration_form_versions
    ADD COLUMN created_by_id VARCHAR(36) NULL,
    ADD COLUMN created_by_name VARCHAR(150) NULL,
    ADD COLUMN updated_by_id VARCHAR(36) NULL,
    ADD COLUMN updated_by_name VARCHAR(150) NULL;

ALTER TABLE form_field_configs
    ADD COLUMN created_by_id VARCHAR(36) NULL,
    ADD COLUMN created_by_name VARCHAR(150) NULL,
    ADD COLUMN updated_by_id VARCHAR(36) NULL,
    ADD COLUMN updated_by_name VARCHAR(150) NULL;

ALTER TABLE attendee_registration_answers
    ADD COLUMN created_by_id VARCHAR(36) NULL,
    ADD COLUMN created_by_name VARCHAR(150) NULL,
    ADD COLUMN updated_by_id VARCHAR(36) NULL,
    ADD COLUMN updated_by_name VARCHAR(150) NULL;

