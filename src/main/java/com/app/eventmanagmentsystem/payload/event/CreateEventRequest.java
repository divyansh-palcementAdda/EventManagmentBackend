package com.app.eventmanagmentsystem.payload.event;

import com.app.eventmanagmentsystem.model.enums.ActivationPolicy;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CreateEventRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    /** Optional: if not supplied, auto-generated from name. */
    @Pattern(regexp = "^[A-Z0-9]{2,20}$", message = "Event code must be 2-20 uppercase alphanumeric characters")
    private String eventCode;

    @Size(max = 300)
    private String venue;

    private Instant startsAt;
    private Instant endsAt;

    @Size(max = 60)
    private String timezone = "UTC";

    private ActivationPolicy activationPolicy = ActivationPolicy.SELF_SERVICE;
    private CheckInPolicy checkInPolicy = CheckInPolicy.SINGLE_ENTRY;

    @Min(1)
    @Max(100000)
    private Integer maximumPasses;

    private Instant activationStartsAt;
    private Instant activationEndsAt;
}

