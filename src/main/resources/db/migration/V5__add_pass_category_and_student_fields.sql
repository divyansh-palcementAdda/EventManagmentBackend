-- ============================================================
-- V5__add_pass_category_and_student_fields.sql
-- Add pass category to event_passes and student details to attendees
-- ============================================================

-- 1. Add pass category (STUDENT, OTHER) to event_passes
ALTER TABLE event_passes
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'STUDENT' AFTER sequence_number,
    ADD KEY idx_ep_category (category);

-- 2. Add student specific fields to attendees
ALTER TABLE attendees
    ADD COLUMN college VARCHAR(150) NULL AFTER designation,
    ADD COLUMN course VARCHAR(100) NULL AFTER college,
    ADD COLUMN year_semester VARCHAR(50) NULL AFTER course,
    ADD COLUMN student_id VARCHAR(50) NULL AFTER year_semester;

