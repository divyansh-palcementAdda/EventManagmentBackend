-- ============================================================
-- V2__multi_day_checkin.sql
-- Multi-day event support, pass validity per event day,
-- and single check-in per pass per permitted event day.
-- ============================================================

-- ===== EVENT_DAYS =====
CREATE TABLE IF NOT EXISTS event_days (
    id              BINARY(16)   NOT NULL,
    event_id        BINARY(16)   NOT NULL,
    day_number      INT          NOT NULL,
    event_date      DATE         NOT NULL,
    label           VARCHAR(100) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by_id   VARCHAR(36),
    created_by_name VARCHAR(150),
    updated_by_id   VARCHAR(36),
    updated_by_name VARCHAR(150),
    PRIMARY KEY (id),
    UNIQUE KEY idx_ed_event_day (event_id, day_number),
    UNIQUE KEY idx_ed_event_date (event_id, event_date),
    CONSTRAINT fk_ed_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== PASS_VALID_DAYS =====
CREATE TABLE IF NOT EXISTS pass_valid_days (
    pass_id         BINARY(16)   NOT NULL,
    event_day_id    BINARY(16)   NOT NULL,
    PRIMARY KEY (pass_id, event_day_id),
    CONSTRAINT fk_pvd_pass FOREIGN KEY (pass_id) REFERENCES event_passes(id) ON DELETE CASCADE,
    CONSTRAINT fk_pvd_day  FOREIGN KEY (event_day_id) REFERENCES event_days(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== UPDATE CHECK_INS FOR MULTI-DAY SUPPORT =====
ALTER TABLE check_ins ADD COLUMN event_day_id BINARY(16) NULL;

-- In MySQL, idx_ci_pass cannot be dropped while fk_ci_pass exists.
-- Drop foreign key first, then index, then re-add foreign key & multi-day unique index.
ALTER TABLE check_ins DROP FOREIGN KEY fk_ci_pass;
ALTER TABLE check_ins DROP INDEX idx_ci_pass;

ALTER TABLE check_ins ADD CONSTRAINT fk_ci_pass
    FOREIGN KEY (pass_id) REFERENCES event_passes(id);

ALTER TABLE check_ins ADD CONSTRAINT fk_ci_event_day
    FOREIGN KEY (event_day_id) REFERENCES event_days(id);

ALTER TABLE check_ins ADD UNIQUE KEY idx_ci_pass_day (pass_id, event_day_id);
