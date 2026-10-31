package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.PassCategory;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An issued event pass.
 *
 * <p>Security design:
 * <ul>
 *   <li>{@code passNumber} is human-readable (e.g. RFC-F5EE57F3-0001) and printed on badges.
 *       It is NOT a secret and must never be used as the sole authorization credential.</li>
 *   <li>{@code tokenHash} is the SHA-256 hash of a 256-bit {@link java.security.SecureRandom} token.
 *       The raw token is returned once at issuance and is never stored in the database.</li>
 * </ul>
 */
@Entity
@Table(name = "event_passes", indexes = {
        @Index(name = "idx_ep_event", columnList = "event_id"),
        @Index(name = "idx_ep_pass_number", columnList = "pass_number", unique = true),
        @Index(name = "idx_ep_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_ep_status", columnList = "status"),
        @Index(name = "idx_ep_attendee", columnList = "attendee_id"),
        @Index(name = "idx_ep_event_sequence", columnList = "event_id, sequence_number")
})
@Getter
@Setter
@NoArgsConstructor
public class EventPass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    /**
     * Human-readable identifier: {EVENT_CODE}-{SERIES_CODE}-{SEQUENCE}.
     * Unique globally (enforced by DB constraint).
     */
    @Column(name = "pass_number", nullable = false, unique = true, length = 40)
    private String passNumber;

    /**
     * Explicit numeric sequence number for reliable range selection and ordering.
     */
    @Column(name = "sequence_number")
    private Long sequenceNumber;

    /**
     * SHA-256 hex digest of the raw activation/QR token.
     * Never store the raw token here.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PassCategory category = PassCategory.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PassStatus status = PassStatus.NOT_ACTIVATED;

    /** Set when a pass is activated. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id", unique = true)
    private Attendee attendee;

    /**
     * Permitted event days for which this pass is valid.
     * If empty, the pass is valid for all days of the event.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pass_valid_days",
            joinColumns = @JoinColumn(name = "pass_id"),
            inverseJoinColumns = @JoinColumn(name = "event_day_id")
    )
    private java.util.Set<EventDay> validDays = new java.util.HashSet<>();

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** UUID string of the user who revoked this pass. */
    @Column(name = "revoked_by_id", length = 36)
    private String revokedById;

    @Column(name = "revoked_by_name", length = 150)
    private String revokedByName;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    /** Idempotency key — prevents duplicate pass generation on client retry. */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;
}

