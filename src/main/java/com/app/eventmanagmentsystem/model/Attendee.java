package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An event attendee — a person registered to a specific event.
 * Attendees are NOT system users. Email uniqueness is enforced per-event, not globally.
 */
@Entity
@Table(name = "attendees", indexes = {
        @Index(name = "idx_attendee_event", columnList = "event_id"),
        @Index(name = "idx_attendee_email_event", columnList = "email, event_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Attendee extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "organization", length = 150)
    private String organization;

    @Column(name = "designation", length = 100)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 30)
    private RegistrationStatus registrationStatus = RegistrationStatus.CONFIRMED;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    /** Normalize email on assignment. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.toLowerCase().trim();
    }
}

