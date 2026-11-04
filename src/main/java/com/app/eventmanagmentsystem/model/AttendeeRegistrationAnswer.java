package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attendee_registration_answers", indexes = {
        @Index(name = "idx_ara_pass", columnList = "pass_id"),
        @Index(name = "idx_ara_attendee", columnList = "attendee_id"),
        @Index(name = "idx_ara_form_ver", columnList = "form_version_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AttendeeRegistrationAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pass_id", nullable = false)
    private EventPass pass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id")
    private Attendee attendee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id")
    private RegistrationFormVersion formVersion;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;
}
