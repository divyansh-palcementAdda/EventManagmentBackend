package com.app.eventmanagmentsystem.payload.attendee;

import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAttendeeRequest {

    @Size(max = 150, message = "Full name cannot exceed 150 characters")
    private String fullName;

    @Size(max = 20, message = "Mobile number cannot exceed 20 characters")
    private String mobileNumber;

    @Size(max = 150, message = "Organization cannot exceed 150 characters")
    private String organization;

    @Size(max = 100, message = "Designation cannot exceed 100 characters")
    private String designation;

    private RegistrationStatus registrationStatus;
}

