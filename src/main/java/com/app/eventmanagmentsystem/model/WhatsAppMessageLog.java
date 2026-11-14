package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_message_logs", indexes = {
        @Index(name = "idx_wml_mobile", columnList = "recipient_mobile"),
        @Index(name = "idx_wml_event", columnList = "event_id"),
        @Index(name = "idx_wml_pass", columnList = "pass_id"),
        @Index(name = "idx_wml_provider_id", columnList = "provider_message_id"),
        @Index(name = "idx_wml_status", columnList = "status"),
        @Index(name = "idx_wml_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppMessageLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "recipient_mobile", nullable = false, length = 20)
    private String recipientMobile;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private WhatsAppNotificationType notificationType;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "pass_id")
    private UUID passId;

    @Column(name = "attendee_id")
    private UUID attendeeId;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
