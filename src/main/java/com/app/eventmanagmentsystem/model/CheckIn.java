package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records a successful event entry for a pass on a specific event day.
 *
 * <p>Invariants:
 * <ul>
 *   <li>One pass → at most one CheckIn per event day (enforced by unique constraint on pass_id + event_day_id).</li>
 *   <li>CheckIn records are immutable after creation — no UPDATE from public APIs.</li>
 * </ul>
 */
@Entity
@Table(name = "check_ins", indexes = {
        @Index(name = "idx_ci_pass_day", columnList = "pass_id, event_day_id", unique = true),
        @Index(name = "idx_ci_event", columnList = "event_id"),
        @Index(name = "idx_ci_attendee", columnList = "attendee_id"),
        @Index(name = "idx_ci_checked_in_at", columnList = "checked_in_at")
})
@Getter
@Setter
@NoArgsConstructor
public class CheckIn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pass_id", nullable = false, updatable = false)
    private EventPass pass;

    /** The specific event day for which this check-in entry occurred. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_day_id", nullable = false, updatable = false)
    private EventDay eventDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendee_id", nullable = false, updatable = false)
    private Attendee attendee;

    /** UUID string of the gatekeeper user who performed check-in. */
    @Column(name = "checked_in_by_id", nullable = false, length = 36, updatable = false)
    private String checkedInById;

    @Column(name = "checked_in_by_name", length = 150, updatable = false)
    private String checkedInByName;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    /**
     * Optional correlation / request ID for tracing duplicate scan attempts.
     */
    @Column(name = "request_id", length = 100, updatable = false)
    private String requestId;
}
