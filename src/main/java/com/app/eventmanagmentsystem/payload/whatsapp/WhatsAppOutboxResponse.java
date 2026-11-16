package com.app.eventmanagmentsystem.payload.whatsapp;

import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.model.enums.WhatsAppOutboxStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppOutboxResponse {

    private UUID id;
    private String recipientMobile;
    private String maskedMobile;
    private String recipientName;
    private WhatsAppNotificationType notificationType;
    private String referenceId;
    private String templateName;
    private WhatsAppOutboxStatus status;
    private int attemptCount;
    private int maxAttempts;
    private String lastError;
    private String lastErrorCategory;
    private Instant nextRetryAt;
    private String providerMessageId;
    private Instant lastDispatchAt;
    private Instant unknownOutcomeAt;
    private String reconciliationStatus;
    private int reconciliationAttempts;
    private Instant createdAt;
    private Instant updatedAt;

    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 6) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }
}
