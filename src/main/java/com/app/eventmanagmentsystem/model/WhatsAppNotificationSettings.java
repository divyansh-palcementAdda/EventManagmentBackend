package com.app.eventmanagmentsystem.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_notification_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppNotificationSettings {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Master integration switch.
     * When false, all outbound WhatsApp activity is completely disabled.
     */
    @Column(name = "integration_enabled", nullable = false)
    @Builder.Default
    private boolean integrationEnabled = false;

    @Column(name = "otp_verification_enabled", nullable = false)
    @Builder.Default
    private boolean otpVerificationEnabled = false;

    @Column(name = "pass_activation_notification_enabled", nullable = false)
    @Builder.Default
    private boolean passActivationNotificationEnabled = false;

    @Column(name = "event_reminder_enabled", nullable = false)
    @Builder.Default
    private boolean eventReminderEnabled = false;

    @Column(name = "otp_template_name", nullable = false, length = 100)
    @Builder.Default
    private String otpTemplateName = "rcf";

    @Column(name = "otp_template_language", nullable = false, length = 10)
    @Builder.Default
    private String otpTemplateLanguage = "en";

    @Column(name = "pass_activation_template_name", nullable = false, length = 100)
    @Builder.Default
    private String passActivationTemplateName = "rcf_activation_pass";

    @Column(name = "pass_activation_template_language", nullable = false, length = 10)
    @Builder.Default
    private String passActivationTemplateLanguage = "en";

    @Column(name = "event_reminder_template_name", nullable = false, length = 100)
    @Builder.Default
    private String eventReminderTemplateName = "reminder_rcf";

    @Column(name = "event_reminder_template_language", nullable = false, length = 10)
    @Builder.Default
    private String eventReminderTemplateLanguage = "en";

    @Column(name = "reminder_lead_hours", nullable = false)
    @Builder.Default
    private int reminderLeadHours = 24;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
