package com.app.eventmanagmentsystem.payload.pass;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkActivationConfirmRequest {

    private String batchId;
    private String idempotencyKey;

    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotEmpty(message = "Assignments list cannot be empty")
    @Valid
    private List<BulkActivationAssignmentDto> assignments;
}

