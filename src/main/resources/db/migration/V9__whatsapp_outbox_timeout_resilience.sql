-- ====================================================================
-- V9: WhatsApp Outbox Timeout Resilience & Delivery State Enhancements
-- Adds fields for timeout handling, atomic leasing, reconciliation, and audit
-- ====================================================================

-- 1. Add resilience and reconciliation columns to whatsapp_outbox
ALTER TABLE whatsapp_outbox ADD COLUMN provider_message_id VARCHAR(100) NULL;
ALTER TABLE whatsapp_outbox ADD COLUMN last_dispatch_at TIMESTAMP NULL;
ALTER TABLE whatsapp_outbox ADD COLUMN unknown_outcome_at TIMESTAMP NULL;
ALTER TABLE whatsapp_outbox ADD COLUMN processing_lease_until TIMESTAMP NULL;
ALTER TABLE whatsapp_outbox ADD COLUMN last_error_category VARCHAR(50) NULL;
ALTER TABLE whatsapp_outbox ADD COLUMN reconciliation_status VARCHAR(50) NULL;
ALTER TABLE whatsapp_outbox ADD COLUMN reconciliation_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE whatsapp_outbox ADD COLUMN correlation_id VARCHAR(64) NULL;

-- 2. Add indexes for atomic worker claiming and provider message tracking
CREATE INDEX idx_wo_status_lease ON whatsapp_outbox (status, processing_lease_until);
CREATE INDEX idx_wo_provider_msg ON whatsapp_outbox (provider_message_id);
