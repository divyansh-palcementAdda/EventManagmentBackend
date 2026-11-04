package com.app.eventmanagmentsystem.payload.pass;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ActivatePassRequest {

    @NotBlank(message = "Raw QR token is required")
    private String rawToken;

    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotBlank(message = "Attendee full name is required")
    @Size(max = 150)
    private String attendeeFullName;

    @NotBlank(message = "Attendee email is required")
    @Email(message = "Invalid email format")
    @Size(max = 180)
    private String attendeeEmail;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid mobile number format")
    private String attendeeMobileNumber;

    @Size(max = 150)
    private String attendeeOrganization;

    @Size(max = 100)
    private String attendeeDesignation;

    @Size(max = 150)
    private String attendeeCollege;

    @Size(max = 100)
    private String attendeeCourse;

    @Size(max = 50)
    private String attendeeYearSemester;

    @Size(max = 50)
    private String attendeeStudentId;

    /** Dynamic field answers key-value map from dynamic form */
    private Map<String, Object> answers;
}
