package com.app.eventmanagmentsystem.payload.allotment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePassAllotmentRequest {

    @NotBlank(message = "Category code is required")
    private String categoryCode;

    @NotNull(message = "Starting sequence number is required")
    @Min(value = 1, message = "Starting sequence number must be at least 1")
    private Long fromSequence;

    @NotNull(message = "Ending sequence number is required")
    @Min(value = 1, message = "Ending sequence number must be at least 1")
    private Long toSequence;

    private String sequenceIdentifier;

    @NotBlank(message = "Allotted to (person/department name) is required")
    private String allottedTo;

    @NotNull(message = "Allotment quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private String remarks;
}
