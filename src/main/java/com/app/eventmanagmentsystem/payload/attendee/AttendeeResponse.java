package com.app.eventmanagmentsystem.payload.attendee;

import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeResponse {

    private UUID id;
    private UUID eventId;
    private String eventCode;
    private String eventName;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String organization;
    private String designation;
    private RegistrationStatus registrationStatus;
    private Instant registeredAt;

    // Associated pass details if claimed/assigned
    private UUID passId;
    private String passNumber;
    private Long sequenceNumber;
    private String passStatus;

    // Check-in details
    private boolean checkedIn;
    private int checkInCount;
    private Instant lastCheckedInAt;

    private Instant createdAt;
    private Instant updatedAt;
}

