package com.app.eventmanagmentsystem.payload.whatsapp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWhatsAppSettingsRequest {

    private boolean integrationEnabled;

    private boolean otpVerificationEnabled;

    private boolean passActivationNotificationEnabled;

    private boolean eventReminderEnabled;

    @NotBlank(message = "OTP template name is required")
    private String otpTemplateName;

    private String otpTemplateLanguage;

    @NotBlank(message = "Pass activation template name is required")
    private String passActivationTemplateName;

    private String passActivationTemplateLanguage;

    @NotBlank(message = "Event reminder template name is required")
    private String eventReminderTemplateName;

    private String eventReminderTemplateLanguage;

    @Min(value = 1, message = "Lead hours must be at least 1")
    @Max(value = 168, message = "Lead hours cannot exceed 168 (7 days)")
    private Integer reminderLeadHours;
}
