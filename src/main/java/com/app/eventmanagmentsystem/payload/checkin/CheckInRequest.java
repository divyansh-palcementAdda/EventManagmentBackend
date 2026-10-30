package com.app.eventmanagmentsystem.payload.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CheckInRequest {

    @NotBlank
    private String rawToken;

    @NotNull
    private UUID eventId;

    /** Optional client request ID for idempotency tracking. */
    private String requestId;
}

