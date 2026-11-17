package com.app.eventmanagmentsystem.payload.allotment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassAllotmentResponse {
    private UUID id;
    private UUID eventId;
    private String categoryCode;
    private String sequenceIdentifier;
    private Long fromSequence;
    private Long toSequence;
    private String allottedTo;
    private Integer allottedQuantity;
    private String remarks;
    private Instant createdAt;
    private String createdByName;
}
