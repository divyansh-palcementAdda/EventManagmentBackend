package com.app.eventmanagmentsystem.payload.pass;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ActivatePassRequest {

    @NotBlank
    private String rawToken;

    @NotNull
    private UUID eventId;

    @NotBlank
    @Size(max = 150)
    private String attendeeFullName;

    @NotBlank
    @Email
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
}

