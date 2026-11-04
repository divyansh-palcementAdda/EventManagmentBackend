package com.app.eventmanagmentsystem.payload.pass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AutoActivatePassRequest {

    @NotBlank(message = "Raw QR token is required")
    private String rawToken;

    @NotNull(message = "Event ID is required")
    private UUID eventId;
}
