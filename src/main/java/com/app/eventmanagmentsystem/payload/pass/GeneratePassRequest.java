package com.app.eventmanagmentsystem.payload.pass;

import com.app.eventmanagmentsystem.model.enums.PassCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class GeneratePassRequest {

    @NotNull
    private UUID eventId;

    @Min(1)
    @Max(500)
    private int batchSize = 1;

    /** Pass category: STUDENT or OTHER. Defaults to STUDENT. */
    private PassCategory category = PassCategory.STUDENT;

    /** Optional client-supplied idempotency key (for batch-of-1 requests). */
    private String idempotencyKey;

    /** Optional specific event days for pass validity. If empty/null, valid for all days of the event. */
    private java.util.List<UUID> validDayIds;
}

