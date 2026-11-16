package com.app.eventmanagmentsystem.model.enums;

public enum WhatsAppOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    DELIVERED,
    RETRY_WAIT,
    UNKNOWN,
    FAILED,
    FAILED_PERMANENTLY,
    SUPPRESSED,
    MANUAL_REVIEW
}
