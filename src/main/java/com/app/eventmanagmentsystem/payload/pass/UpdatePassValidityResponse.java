package com.app.eventmanagmentsystem.payload.pass;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePassValidityResponse {

    private UUID eventId;
    private int updatedPassesCount;
    private int skippedPassesCount;
    private List<String> validDayLabels;
    private Instant updatedAt;
    private String message;
}

