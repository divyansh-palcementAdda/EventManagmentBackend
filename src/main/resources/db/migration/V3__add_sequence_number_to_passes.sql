-- V3__add_sequence_number_to_passes.sql
-- Add numeric sequence_number to event_passes for reliable range querying and sorting

ALTER TABLE event_passes ADD COLUMN sequence_number BIGINT NULL;

UPDATE event_passes
SET sequence_number = CAST(SUBSTRING_INDEX(pass_number, '-', -1) AS UNSIGNED)
WHERE sequence_number IS NULL;

CREATE INDEX idx_ep_event_sequence ON event_passes (event_id, sequence_number);

