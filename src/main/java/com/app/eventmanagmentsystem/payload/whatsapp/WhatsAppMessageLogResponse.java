package com.app.eventmanagmentsystem.payload.whatsapp;

import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppMessageLogResponse {

    private UUID id;
    private String recipientMobile;
    private String maskedMobile;
    private WhatsAppNotificationType notificationType;
    private UUID eventId;
    private String eventName;
    private UUID passId;
    private String passNumber;
    private String templateName;
    private String providerMessageId;
    private String status;
    private String errorCode;
    private String errorMessage;
    private int retryCount;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant failedAt;
    private Instant createdAt;

    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        int len = phone.length();
        return phone.substring(0, 3) + "****" + phone.substring(len - 3);
    }
}
