package com.app.eventmanagmentsystem.payload.walkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class WalkInAdmitRequest {

    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotNull(message = "Event Day ID is required")
    private UUID eventDayId;

    @NotNull(message = "Event Pass Category ID is required")
    private UUID eventCategoryId;

    /** If selecting an existing attendee, pass attendeeId. Otherwise new attendee is created. */
    private UUID existingAttendeeId;

    @NotBlank(message = "Attendee Full Name is required")
    private String fullName;

    private String email;
    private String mobileNumber;
    private String organization;
    private String designation;

    /** Dynamic field answers key-value map */
    private Map<String, Object> answers;
}
