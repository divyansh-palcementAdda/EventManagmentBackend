package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import com.app.eventmanagmentsystem.util.PhoneNumberUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An event attendee — a person registered to a specific event.
 * Mobile number uniqueness is enforced per-event (one mobile number per attendee per event).
 */
@Entity
@Table(name = "attendees", indexes = {
        @Index(name = "idx_attendee_event", columnList = "event_id"),
        @Index(name = "idx_attendee_email_event", columnList = "email, event_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_attendee_event_mobile", columnNames = {"event_id", "normalized_mobile_number"})
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

    @Column(name = "mobile_number", length = 30)
    private String mobileNumber;

    @Column(name = "normalized_mobile_number", length = 30)
    private String normalizedMobileNumber;

    @Column(name = "organization", length = 150)
    private String organization;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "college", length = 150)
    private String college;

    @Column(name = "course", length = 100)
    private String course;

    @Column(name = "year_semester", length = 50)
    private String yearSemester;

    @Column(name = "student_id", length = 50)
    private String studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 30)
    private RegistrationStatus registrationStatus = RegistrationStatus.CONFIRMED;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    /** Normalize email on assignment. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.toLowerCase().trim();
    }

    /** Normalize mobile number on assignment. */
    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber != null ? mobileNumber.trim() : null;
        this.normalizedMobileNumber = PhoneNumberUtil.normalize(mobileNumber);
    }
}


