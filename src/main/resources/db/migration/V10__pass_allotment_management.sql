-- ============================================================
-- V10__pass_allotment_management.sql
-- Pass Allotment Management: tracking pass assignments to internal
-- representatives, with category, sequence range, and audit fields.
-- ============================================================

CREATE TABLE IF NOT EXISTS pass_allotments (
    id                  BINARY(16)   NOT NULL,
    event_id            BINARY(16)   NOT NULL,
    category_id         BINARY(16)   NULL,
    category_code       VARCHAR(50)  NOT NULL,
    sequence_identifier VARCHAR(120) NOT NULL,
    from_sequence       BIGINT       NOT NULL,
    to_sequence         BIGINT       NOT NULL,
    allotted_to         VARCHAR(150) NOT NULL,
    allotted_quantity   INT          NOT NULL,
    remarks             VARCHAR(500) NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by_id       VARCHAR(36)  NULL,
    created_by_name     VARCHAR(150) NULL,
    updated_by_id       VARCHAR(36)  NULL,
    updated_by_name     VARCHAR(150) NULL,
    PRIMARY KEY (id),
    KEY idx_pa_event (event_id),
    KEY idx_pa_allotted_to (event_id, allotted_to),
    KEY idx_pa_category (event_id, category_code),
    CONSTRAINT fk_pa_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_category FOREIGN KEY (category_id) REFERENCES pass_categories (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE event_passes ADD COLUMN allotment_id BINARY(16) NULL;
ALTER TABLE event_passes ADD CONSTRAINT fk_ep_allotment FOREIGN KEY (allotment_id) REFERENCES pass_allotments (id) ON DELETE SET NULL;
CREATE INDEX idx_ep_allotment ON event_passes (allotment_id);
