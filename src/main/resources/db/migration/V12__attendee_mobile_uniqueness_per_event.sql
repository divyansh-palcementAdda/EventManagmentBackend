-- ============================================================
-- V12__attendee_mobile_uniqueness_per_event.sql
-- Enforce normalized mobile number uniqueness per event for attendees.
-- ============================================================

ALTER TABLE attendees MODIFY COLUMN mobile_number VARCHAR(30) NULL;
ALTER TABLE attendees ADD COLUMN normalized_mobile_number VARCHAR(30) NULL AFTER mobile_number;

-- Backfill normalized mobile number for existing records
UPDATE attendees
SET normalized_mobile_number =
    CASE
        WHEN mobile_number IS NULL OR TRIM(mobile_number) = '' THEN NULL
        WHEN LENGTH(REGEXP_REPLACE(mobile_number, '[^0-9]', '')) = 10 
             THEN CONCAT('+91', REGEXP_REPLACE(mobile_number, '[^0-9]', ''))
        WHEN LENGTH(REGEXP_REPLACE(mobile_number, '[^0-9]', '')) = 11 AND REGEXP_REPLACE(mobile_number, '[^0-9]', '') LIKE '0%'
             THEN CONCAT('+91', SUBSTRING(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 2))
        WHEN LENGTH(REGEXP_REPLACE(mobile_number, '[^0-9]', '')) >= 7 
             THEN CONCAT('+', REGEXP_REPLACE(mobile_number, '[^0-9]', ''))
        ELSE NULL
    END;

ALTER TABLE attendees ADD CONSTRAINT uq_attendee_event_mobile UNIQUE (event_id, normalized_mobile_number);
