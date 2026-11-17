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
public class PassAllotmentItemResponse {
    private UUID passId;
    private String passNumber;
    private Long sequenceNumber;
    private String category;
    private String categoryName;
    private String status;
    private String attendeeName;
    private String attendeeEmail;
    private boolean checkedIn;
    private Instant firstCheckInTime;
    private Instant activatedAt;
}
