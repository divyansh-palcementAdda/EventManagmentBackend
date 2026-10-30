package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log record for sensitive business operations.
 *
 * <p>Does NOT extend {@link com.app.eventmanagmentsystem.model.audit.BaseEntity}
 * because audit logs are self-contained and must not themselves be audited
 * (which would cause recursion). They carry their own {@code id} and {@code createdAt}.
 *
 * <p>Passwords, JWTs, raw QR tokens, and other secrets must never be stored here.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_al_actor", columnList = "actor_id"),
        @Index(name = "idx_al_event", columnList = "event_id"),
        @Index(name = "idx_al_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_al_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 80)
    private String entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public AuditLog(String actorId, String actorName, String action,
                    String entityType, String entityId, String eventId,
                    AuditOutcome outcome, String requestId, String details) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventId = eventId;
        this.outcome = outcome;
        this.requestId = requestId;
        this.details = details;
        this.createdAt = Instant.now();
    }
}

