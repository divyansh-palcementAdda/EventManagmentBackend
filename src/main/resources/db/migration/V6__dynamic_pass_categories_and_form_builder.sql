-- ============================================================
-- V6__dynamic_pass_categories_and_form_builder.sql
-- Dynamic pass categories, event-category configuration,
-- dynamic form schemas, versioning, and walk-in support.
-- ============================================================

-- 1. Pass Categories (Global and Event-Specific)
CREATE TABLE IF NOT EXISTS pass_categories (
    id                  BINARY(16)   NOT NULL,
    name                VARCHAR(100) NOT NULL,
    code                VARCHAR(50)  NOT NULL,
    scope               VARCHAR(30)  NOT NULL DEFAULT 'GLOBAL',
    event_id            BINARY(16)   NULL,
    description         VARCHAR(500) NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order       INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          VARCHAR(100) NULL,
    updated_by          VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_pc_scope (scope),
    KEY idx_pc_event (event_id),
    KEY idx_pc_code (code),
    UNIQUE KEY uq_pc_scope_code_event (scope, code, event_id),
    CONSTRAINT fk_pc_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Event Pass Category Configuration (Event-Specific Category Policies)
CREATE TABLE IF NOT EXISTS event_pass_categories (
    id                  BINARY(16)   NOT NULL,
    event_id            BINARY(16)   NOT NULL,
    category_id         BINARY(16)   NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    activation_mode     VARCHAR(30)  NOT NULL DEFAULT 'SELF_SERVICE',
    qr_required         BOOLEAN      NOT NULL DEFAULT TRUE,
    identity_policy     VARCHAR(30)  NOT NULL DEFAULT 'COLLECT_IDENTITY',
    display_order       INT          NOT NULL DEFAULT 0,
    pass_limit          INT          NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          VARCHAR(100) NULL,
    updated_by          VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_epc_event_category (event_id, category_id),
    KEY idx_epc_event (event_id),
    KEY idx_epc_category (category_id),
    CONSTRAINT fk_epc_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_epc_category FOREIGN KEY (category_id) REFERENCES pass_categories (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Registration Form Versions (Immutable Form Snapshots)
CREATE TABLE IF NOT EXISTS registration_form_versions (
    id                      BINARY(16)   NOT NULL,
    event_pass_category_id  BINARY(16)   NOT NULL,
    version_number          INT          NOT NULL DEFAULT 1,
    published               BOOLEAN      NOT NULL DEFAULT TRUE,
    schema_snapshot         LONGTEXT     NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    created_by              VARCHAR(100) NULL,
    updated_by              VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rfv_epc_version (event_pass_category_id, version_number),
    KEY idx_rfv_epc (event_pass_category_id),
    CONSTRAINT fk_rfv_epc FOREIGN KEY (event_pass_category_id) REFERENCES event_pass_categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Form Field Configurations (Draft / Live Field Definitions for Form Builder)
CREATE TABLE IF NOT EXISTS form_field_configs (
    id                      BINARY(16)   NOT NULL,
    event_pass_category_id  BINARY(16)   NOT NULL,
    field_key               VARCHAR(100) NOT NULL,
    label                   VARCHAR(200) NOT NULL,
    description             VARCHAR(500) NULL,
    placeholder             VARCHAR(200) NULL,
    field_type              VARCHAR(30)  NOT NULL DEFAULT 'TEXT',
    required                BOOLEAN      NOT NULL DEFAULT FALSE,
    visible                 BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order           INT          NOT NULL DEFAULT 0,
    default_value           VARCHAR(255) NULL,
    validation_rules        TEXT         NULL,
    options                 TEXT         NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    created_by              VARCHAR(100) NULL,
    updated_by              VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_ffc_epc (event_pass_category_id),
    KEY idx_ffc_epc_order (event_pass_category_id, display_order),
    CONSTRAINT fk_ffc_epc FOREIGN KEY (event_pass_category_id) REFERENCES event_pass_categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Attendee Registration Dynamic Answers (Historical Field Responses)
CREATE TABLE IF NOT EXISTS attendee_registration_answers (
    id                  BINARY(16)   NOT NULL,
    pass_id             BINARY(16)   NOT NULL,
    attendee_id         BINARY(16)   NULL,
    form_version_id     BINARY(16)   NULL,
    field_key           VARCHAR(100) NOT NULL,
    field_value         TEXT         NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          VARCHAR(100) NULL,
    updated_by          VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_ara_pass (pass_id),
    KEY idx_ara_attendee (attendee_id),
    KEY idx_ara_form_ver (form_version_id),
    CONSTRAINT fk_ara_pass FOREIGN KEY (pass_id) REFERENCES event_passes (id) ON DELETE CASCADE,
    CONSTRAINT fk_ara_attendee FOREIGN KEY (attendee_id) REFERENCES attendees (id) ON DELETE SET NULL,
    CONSTRAINT fk_ara_form_ver FOREIGN KEY (form_version_id) REFERENCES registration_form_versions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Additive Schema Updates for event_passes
ALTER TABLE event_passes
    ADD COLUMN category_id BINARY(16) NULL AFTER category,
    ADD COLUMN event_category_id BINARY(16) NULL AFTER category_id,
    MODIFY COLUMN token_hash VARCHAR(64) NULL,
    MODIFY COLUMN category VARCHAR(50) NOT NULL DEFAULT 'STUDENT',
    ADD KEY idx_ep_category_id (category_id),
    ADD KEY idx_ep_event_category_id (event_category_id);

-- 7. Additive Schema Updates for check_ins (allow null attendee for NO_IDENTITY)
ALTER TABLE check_ins
    MODIFY COLUMN attendee_id BINARY(16) NULL;

-- 8. Seed Initial Global Categories
INSERT INTO pass_categories (id, name, code, scope, event_id, description, active, display_order, created_at, updated_at)
VALUES
    (UNHEX(REPLACE('00000000-0000-0000-0000-000000000001', '-', '')), 'Student', 'STUDENT', 'GLOBAL', NULL, 'Standard student registration pass requiring academic verification.', TRUE, 1, NOW(), NOW()),
    (UNHEX(REPLACE('00000000-0000-0000-0000-000000000002', '-', '')), 'Other / General', 'OTHER', 'GLOBAL', NULL, 'General attendee registration pass for professionals and guests.', TRUE, 2, NOW(), NOW()),
    (UNHEX(REPLACE('00000000-0000-0000-0000-000000000003', '-', '')), 'VIP', 'VIP', 'GLOBAL', NULL, 'Special access VIP pass with expedited entry.', TRUE, 3, NOW(), NOW()),
    (UNHEX(REPLACE('00000000-0000-0000-0000-000000000004', '-', '')), 'Faculty', 'FACULTY', 'GLOBAL', NULL, 'University and institution faculty members pass.', TRUE, 4, NOW(), NOW()),
    (UNHEX(REPLACE('00000000-0000-0000-0000-000000000005', '-', '')), 'Sponsor', 'SPONSOR', 'GLOBAL', NULL, 'Event partners and sponsor pass.', TRUE, 5, NOW(), NOW()),
    (UNHEX(REPLACE('00000000-0000-0000-0000-000000000006', '-', '')), 'Guest', 'GUEST', 'GLOBAL', NULL, 'Special invited guests and delegates pass.', TRUE, 6, NOW(), NOW());

-- 9. Auto-seed event_pass_categories for existing events (backfill STUDENT and OTHER)
INSERT INTO event_pass_categories (id, event_id, category_id, enabled, activation_mode, qr_required, identity_policy, display_order, created_at, updated_at)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    e.id,
    c.id,
    TRUE,
    'SELF_SERVICE',
    TRUE,
    'COLLECT_IDENTITY',
    c.display_order,
    NOW(),
    NOW()
FROM events e
CROSS JOIN pass_categories c
WHERE c.code IN ('STUDENT', 'OTHER')
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 10. Backfill category_id and event_category_id on existing event_passes
UPDATE event_passes ep
JOIN pass_categories pc ON pc.code = ep.category AND pc.scope = 'GLOBAL'
SET ep.category_id = pc.id
WHERE ep.category_id IS NULL;

UPDATE event_passes ep
JOIN event_pass_categories epc ON epc.event_id = ep.event_id AND epc.category_id = ep.category_id
SET ep.event_category_id = epc.id
WHERE ep.event_category_id IS NULL;
