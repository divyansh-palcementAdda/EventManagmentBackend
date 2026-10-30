package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.ActivationPolicy;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An event for which passes are issued.
 */
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_event_code", columnList = "event_code", unique = true),
        @Index(name = "idx_events_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Event extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Short uppercase code derived from event name (e.g. "RFC" for Renaissance Coffee Festival).
     * Must be unique. Administrators can set/edit before publication.
     */
    @Column(name = "event_code", nullable = false, unique = true, length = 20)
    private String eventCode;

    /**
     * Hex string representing the random series code for pass numbering, e.g. "F5EE57F3".
     * Generated once when the first pass series is initialized for this event.
     */
    @Column(name = "pass_series_code", length = 20)
    private String passSeriesCode;

    /**
     * Atomic sequence counter used to generate sequential pass numbers.
     * Updated via locked SQL: UPDATE events SET pass_sequence_counter = pass_sequence_counter + 1 WHERE id = ?
     */
    @Column(name = "pass_sequence_counter", nullable = false)
    private long passSequenceCounter = 0L;

    @Column(name = "venue", length = 300)
    private String venue;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "timezone", length = 60)
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventStatus status = EventStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "activation_policy", nullable = false, length = 30)
    private ActivationPolicy activationPolicy = ActivationPolicy.SELF_SERVICE;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_policy", nullable = false, length = 30)
    private CheckInPolicy checkInPolicy = CheckInPolicy.SINGLE_ENTRY;

    /** Optional cap on total passes for this event. Null = unlimited. */
    @Column(name = "maximum_passes")
    private Integer maximumPasses;

    /** Optional window: passes may only be activated after this time. */
    @Column(name = "activation_starts_at")
    private Instant activationStartsAt;

    /** Optional window: passes may only be activated before this time. */
    @Column(name = "activation_ends_at")
    private Instant activationEndsAt;
}

