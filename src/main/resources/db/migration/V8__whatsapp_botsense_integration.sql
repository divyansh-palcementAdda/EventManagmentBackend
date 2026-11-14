-- ====================================================================
-- V8: WhatsApp Botsense Integration Schema
-- Multi-tier Independent Toggles: Master, OTP, Pass Activation, Reminders
-- ====================================================================

-- 1. WhatsApp Notification Settings Table
CREATE TABLE IF NOT EXISTS whatsapp_notification_settings (
    id BINARY(16) NOT NULL PRIMARY KEY,
    integration_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    otp_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    pass_activation_notification_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    event_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    otp_template_name VARCHAR(100) NOT NULL DEFAULT 'rcf',
    otp_template_language VARCHAR(10) NOT NULL DEFAULT 'en',
    pass_activation_template_name VARCHAR(100) NOT NULL DEFAULT 'rcf_activation_pass',
    pass_activation_template_language VARCHAR(10) NOT NULL DEFAULT 'en',
    event_reminder_template_name VARCHAR(100) NOT NULL DEFAULT 'reminder_rcf',
    event_reminder_template_language VARCHAR(10) NOT NULL DEFAULT 'en',
    reminder_lead_hours INT NOT NULL DEFAULT 24,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(150) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initialize default single-row settings if not present
INSERT INTO whatsapp_notification_settings (
    id, integration_enabled, otp_verification_enabled,
    pass_activation_notification_enabled, event_reminder_enabled,
    otp_template_name, otp_template_language,
    pass_activation_template_name, pass_activation_template_language,
    event_reminder_template_name, event_reminder_template_language,
    reminder_lead_hours, updated_by
)
SELECT 
    UNHEX(REPLACE('11111111-1111-1111-1111-111111111111', '-', '')),
    FALSE, FALSE, FALSE, FALSE,
    'rcf', 'en',
    'rcf_activation_pass', 'en',
    'reminder_rcf', 'en',
    24, 'system'
WHERE NOT EXISTS (SELECT 1 FROM whatsapp_notification_settings);

-- 2. WhatsApp OTP Verifications Table
CREATE TABLE IF NOT EXISTS whatsapp_otp_verifications (
    id BINARY(16) NOT NULL PRIMARY KEY,
    mobile_number VARCHAR(20) NOT NULL,
    event_id BINARY(16) NULL,
    pass_id BINARY(16) NULL,
    otp_hash VARCHAR(64) NOT NULL,
    verification_token VARCHAR(64) NULL,
    expires_at TIMESTAMP NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    resend_count INT NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wov_mobile_event (mobile_number, event_id),
    INDEX idx_wov_token (verification_token),
    INDEX idx_wov_expires (expires_at),
    CONSTRAINT fk_wov_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_wov_pass FOREIGN KEY (pass_id) REFERENCES event_passes (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. WhatsApp Outbox Table (Transactional queue)
CREATE TABLE IF NOT EXISTS whatsapp_outbox (
    id BINARY(16) NOT NULL PRIMARY KEY,
    recipient_mobile VARCHAR(20) NOT NULL,
    recipient_name VARCHAR(150) NULL,
    notification_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(36) NULL,
    event_id BINARY(16) NULL,
    template_name VARCHAR(100) NOT NULL,
    payload TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    last_error TEXT NULL,
    next_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wo_status_retry (status, next_retry_at),
    INDEX idx_wo_reference (reference_id, notification_type),
    CONSTRAINT fk_wo_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. WhatsApp Message Logs Table (Audit & Delivery History)
CREATE TABLE IF NOT EXISTS whatsapp_message_logs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    recipient_mobile VARCHAR(20) NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    event_id BINARY(16) NULL,
    pass_id BINARY(16) NULL,
    attendee_id BINARY(16) NULL,
    template_name VARCHAR(100) NULL,
    provider_message_id VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SENT',
    error_code VARCHAR(50) NULL,
    error_message TEXT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    sent_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    read_at TIMESTAMP NULL,
    failed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wml_mobile (recipient_mobile),
    INDEX idx_wml_event (event_id),
    INDEX idx_wml_pass (pass_id),
    INDEX idx_wml_provider_id (provider_message_id),
    INDEX idx_wml_status (status),
    INDEX idx_wml_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
