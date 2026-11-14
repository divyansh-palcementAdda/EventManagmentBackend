package com.app.eventmanagmentsystem.payload.whatsapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SendOtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid mobile number format")
    private String mobileNumber;

    private UUID eventId;

    private String rawToken;
}
