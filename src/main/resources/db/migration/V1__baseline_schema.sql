-- ============================================================
-- V1__baseline_schema.sql
-- EventPass Management System - Baseline Schema
-- All UUIDs stored as BINARY(16) for efficient indexing.
-- All timestamps in UTC.
-- ============================================================

SET time_zone = '+00:00';

-- ===== PERMISSIONS =====
CREATE TABLE IF NOT EXISTS permissions (
    id              BINARY(16)   NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(255),
    resource        VARCHAR(60),
    action          VARCHAR(60),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by_id   VARCHAR(36),
    created_by_name VARCHAR(150),
    updated_by_id   VARCHAR(36),
    updated_by_name VARCHAR(150),
    PRIMARY KEY (id),
    UNIQUE KEY idx_permissions_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== ROLES =====
CREATE TABLE IF NOT EXISTS roles (
    id              BINARY(16)   NOT NULL,
    name            VARCHAR(60)  NOT NULL,
    description     VARCHAR(255),
    system_defined  TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by_id   VARCHAR(36),
    created_by_name VARCHAR(150),
    updated_by_id   VARCHAR(36),
    updated_by_name VARCHAR(150),
    PRIMARY KEY (id),
    UNIQUE KEY idx_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== ROLE_PERMISSIONS (join table) =====
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id         BINARY(16)   NOT NULL,
    permission_id   BINARY(16)   NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles(id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== USERS =====
CREATE TABLE IF NOT EXISTS users (
    id              BINARY(16)   NOT NULL,
    email           VARCHAR(180) NOT NULL,
    password_hash   VARCHAR(72)  NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    mobile_number   VARCHAR(20),
    account_status  VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by_id   VARCHAR(36),
    created_by_name VARCHAR(150),
    updated_by_id   VARCHAR(36),
    updated_by_name VARCHAR(150),
    PRIMARY KEY (id),
    UNIQUE KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== USER_ROLES (join table) =====
CREATE TABLE IF NOT EXISTS user_roles (
    user_id         BINARY(16)   NOT NULL,
    role_id         BINARY(16)   NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== EVENTS =====
CREATE TABLE IF NOT EXISTS events (
    id                      BINARY(16)   NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    description             TEXT,
    event_code              VARCHAR(20)  NOT NULL,
    pass_series_code        VARCHAR(20),
    pass_sequence_counter   BIGINT       NOT NULL DEFAULT 0,
    venue                   VARCHAR(300),
    starts_at               DATETIME(6),
    ends_at                 DATETIME(6),
    timezone                VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    status                  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    activation_policy       VARCHAR(30)  NOT NULL DEFAULT 'SELF_SERVICE',
    check_in_policy         VARCHAR(30)  NOT NULL DEFAULT 'SINGLE_ENTRY',
    maximum_passes          INT,
    activation_starts_at    DATETIME(6),
    activation_ends_at      DATETIME(6),
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    created_by_id           VARCHAR(36),
    created_by_name         VARCHAR(150),
    updated_by_id           VARCHAR(36),
    updated_by_name         VARCHAR(150),
    PRIMARY KEY (id),
    UNIQUE KEY idx_events_event_code (event_code),
    KEY idx_events_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== EVENT_GATEKEEPERS =====
CREATE TABLE IF NOT EXISTS event_gatekeepers (
    id                  BINARY(16)   NOT NULL,
    event_id            BINARY(16)   NOT NULL,
    user_id             BINARY(16)   NOT NULL,
    assigned_at         DATETIME(6)  NOT NULL,
    assigned_by_id      VARCHAR(36),
    assigned_by_name    VARCHAR(150),
    active              TINYINT(1)   NOT NULL DEFAULT 1,
    deactivated_at      DATETIME(6),
    deactivated_by_id   VARCHAR(36),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by_id       VARCHAR(36),
    created_by_name     VARCHAR(150),
    updated_by_id       VARCHAR(36),
    updated_by_name     VARCHAR(150),
    PRIMARY KEY (id),
    KEY idx_eg_event_user  (event_id, user_id),
    KEY idx_eg_event_active (event_id, active),
    CONSTRAINT fk_eg_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT fk_eg_user  FOREIGN KEY (user_id)  REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== ATTENDEES =====
CREATE TABLE IF NOT EXISTS attendees (
    id                  BINARY(16)   NOT NULL,
    event_id            BINARY(16)   NOT NULL,
    full_name           VARCHAR(150) NOT NULL,
    email               VARCHAR(180) NOT NULL,
    mobile_number       VARCHAR(20),
    organization        VARCHAR(150),
    designation         VARCHAR(100),
    registration_status VARCHAR(30)  NOT NULL DEFAULT 'CONFIRMED',
    registered_at       DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by_id       VARCHAR(36),
    created_by_name     VARCHAR(150),
    updated_by_id       VARCHAR(36),
    updated_by_name     VARCHAR(150),
    PRIMARY KEY (id),
    KEY idx_attendee_event        (event_id),
    KEY idx_attendee_email_event  (email, event_id),
    CONSTRAINT fk_att_event FOREIGN KEY (event_id) REFERENCES events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== EVENT_PASSES =====
CREATE TABLE IF NOT EXISTS event_passes (
    id                  BINARY(16)   NOT NULL,
    event_id            BINARY(16)   NOT NULL,
    attendee_id         BINARY(16)   UNIQUE,
    pass_number         VARCHAR(40)  NOT NULL,
    token_hash          VARCHAR(64)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'NOT_ACTIVATED',
    generated_at        DATETIME(6)  NOT NULL,
    activated_at        DATETIME(6),
    revoked_at          DATETIME(6),
    revoked_by_id       VARCHAR(36),
    revoked_by_name     VARCHAR(150),
    revocation_reason   VARCHAR(500),
    idempotency_key     VARCHAR(100) UNIQUE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by_id       VARCHAR(36),
    created_by_name     VARCHAR(150),
    updated_by_id       VARCHAR(36),
    updated_by_name     VARCHAR(150),
    PRIMARY KEY (id),
    UNIQUE KEY idx_ep_pass_number  (pass_number),
    UNIQUE KEY idx_ep_token_hash   (token_hash),
    KEY        idx_ep_event        (event_id),
    KEY        idx_ep_status       (status),
    KEY        idx_ep_attendee     (attendee_id),
    CONSTRAINT fk_ep_event    FOREIGN KEY (event_id)    REFERENCES events(id),
    CONSTRAINT fk_ep_attendee FOREIGN KEY (attendee_id) REFERENCES attendees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== CHECK_INS =====
CREATE TABLE IF NOT EXISTS check_ins (
    id                  BINARY(16)   NOT NULL,
    event_id            BINARY(16)   NOT NULL,
    pass_id             BINARY(16)   NOT NULL,
    attendee_id         BINARY(16)   NOT NULL,
    checked_in_by_id    VARCHAR(36)  NOT NULL,
    checked_in_by_name  VARCHAR(150),
    checked_in_at       DATETIME(6)  NOT NULL,
    request_id          VARCHAR(100),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by_id       VARCHAR(36),
    created_by_name     VARCHAR(150),
    updated_by_id       VARCHAR(36),
    updated_by_name     VARCHAR(150),
    PRIMARY KEY (id),
    UNIQUE KEY idx_ci_pass         (pass_id),
    KEY        idx_ci_event        (event_id),
    KEY        idx_ci_attendee     (attendee_id),
    KEY        idx_ci_checked_in_at (checked_in_at),
    CONSTRAINT fk_ci_event    FOREIGN KEY (event_id)    REFERENCES events(id),
    CONSTRAINT fk_ci_pass     FOREIGN KEY (pass_id)     REFERENCES event_passes(id),
    CONSTRAINT fk_ci_attendee FOREIGN KEY (attendee_id) REFERENCES attendees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== AUDIT_LOGS =====
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BINARY(16)   NOT NULL,
    actor_id    VARCHAR(36),
    actor_name  VARCHAR(150),
    action      VARCHAR(80)  NOT NULL,
    entity_type VARCHAR(80),
    entity_id   VARCHAR(36),
    event_id    VARCHAR(36),
    outcome     VARCHAR(20)  NOT NULL,
    request_id  VARCHAR(100),
    details     TEXT,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_al_actor      (actor_id),
    KEY idx_al_event      (event_id),
    KEY idx_al_entity     (entity_type, entity_id),
    KEY idx_al_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== EMAIL_OUTBOX =====
CREATE TABLE IF NOT EXISTS email_outbox (
    id                  BINARY(16)   NOT NULL,
    recipient_email     VARCHAR(180) NOT NULL,
    recipient_name      VARCHAR(150),
    notification_type   VARCHAR(40)  NOT NULL,
    reference_id        VARCHAR(36),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count       INT          NOT NULL DEFAULT 0,
    max_attempts        INT          NOT NULL DEFAULT 5,
    next_retry_at       DATETIME(6),
    last_error          VARCHAR(500),
    payload             TEXT,
    created_at          DATETIME(6)  NOT NULL,
    sent_at             DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_eo_status_next_retry (status, next_retry_at),
    KEY idx_eo_reference_id      (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

