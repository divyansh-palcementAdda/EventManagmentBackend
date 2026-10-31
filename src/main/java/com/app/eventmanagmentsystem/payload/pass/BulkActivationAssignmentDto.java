package com.app.eventmanagmentsystem.payload.pass;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkActivationAssignmentDto {

    @NotNull(message = "Pass ID is required")
    private UUID passId;

    @NotBlank(message = "Attendee name is required")
    private String attendeeFullName;

    @NotBlank(message = "Attendee email is required")
    @Email(message = "Valid email is required")
    private String attendeeEmail;

    private String attendeeMobileNumber;
    private String attendeeOrganization;
    private String attendeeDesignation;
}

