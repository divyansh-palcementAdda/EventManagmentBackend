package com.app.eventmanagmentsystem.payload.whatsapp;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppSettingsResponse {

    private UUID id;
    private boolean integrationEnabled;
    private boolean otpVerificationEnabled;
    private boolean passActivationNotificationEnabled;
    private boolean eventReminderEnabled;

    private String otpTemplateName;
    private String otpTemplateLanguage;

    private String passActivationTemplateName;
    private String passActivationTemplateLanguage;

    private String eventReminderTemplateName;
    private String eventReminderTemplateLanguage;

    private int reminderLeadHours;

    // Sender and connectivity status
    private String senderPhoneNumber;
    private String fromNumberId;
    private boolean configured;
    private boolean connected;
    private String lastConnectionError;

    private Instant updatedAt;
    private String updatedBy;
}
