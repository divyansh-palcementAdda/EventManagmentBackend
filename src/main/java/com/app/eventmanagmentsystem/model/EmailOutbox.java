package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.enums.EmailOutboxStatus;
import com.app.eventmanagmentsystem.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox pattern for reliable email delivery.
 *
 * <p>Written in the same transaction as pass activation.
 * A scheduler picks up PENDING records, attempts delivery, and updates the status.
 * If SMTP fails, the record remains PENDING (or FAILED after max retries), and
 * activation is not rolled back.
 */
@Entity
@Table(name = "email_outbox", indexes = {
        @Index(name = "idx_eo_status_next_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_eo_reference_id", columnList = "reference_id")
})
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "recipient_email", nullable = false, length = 180)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType notificationType;

    /**
     * UUID of the associated record (e.g. EventPass.id or Attendee.id) for deduplication.
     */
    @Column(name = "reference_id", length = 36)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    /** Payload: serialized template data (JSON). */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.nextRetryAt == null) {
            this.nextRetryAt = Instant.now();
        }
    }
}

