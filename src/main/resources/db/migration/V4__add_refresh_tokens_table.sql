-- V4__add_refresh_tokens_table.sql
-- Persistent refresh tokens for secure session management and token rotation

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                     BINARY(16)   NOT NULL,
    user_id                BINARY(16)   NOT NULL,
    token_hash             VARCHAR(64)  NOT NULL,
    expires_at             DATETIME(6)  NOT NULL,
    revoked_at             DATETIME(6)  NULL,
    revocation_reason      VARCHAR(200) NULL,
    replaced_by_token_hash VARCHAR(64)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    created_by_id          VARCHAR(36)  NULL,
    created_by_name        VARCHAR(150) NULL,
    updated_by_id          VARCHAR(36)  NULL,
    updated_by_name        VARCHAR(150) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_rt_token_hash (token_hash),
    KEY idx_rt_user_id (user_id),
    KEY idx_rt_expires_at (expires_at),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

