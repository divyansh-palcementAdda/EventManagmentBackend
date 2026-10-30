package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Assignment of a system User (with GATEKEEPER role) to an Event.
 *
 * Soft deactivation is used rather than hard delete to preserve assignment history.
 * Uniqueness: only one ACTIVE assignment per (event, user) pair is allowed.
 */
@Entity
@Table(name = "event_gatekeepers", indexes = {
        @Index(name = "idx_eg_event_user", columnList = "event_id, user_id"),
        @Index(name = "idx_eg_event_active", columnList = "event_id, active")
})
@Getter
@Setter
@NoArgsConstructor
public class EventGatekeeper extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    /** UUID of the admin who made the assignment. */
    @Column(name = "assigned_by_id", length = 36)
    private String assignedById;

    @Column(name = "assigned_by_name", length = 150)
    private String assignedByName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "deactivated_by_id", length = 36)
    private String deactivatedById;
}

