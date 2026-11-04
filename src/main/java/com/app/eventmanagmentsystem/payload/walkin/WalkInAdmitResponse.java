package com.app.eventmanagmentsystem.payload.walkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalkInAdmitResponse {
    private UUID checkInId;
    private UUID passId;
    private String passNumber;
    private UUID attendeeId;
    private String attendeeName;
    private String attendeeEmail;
    private UUID eventId;
    private String eventName;
    private UUID eventDayId;
    private Integer dayNumber;
    private String categoryName;
    private String categoryCode;
    private Instant admittedAt;
    private String admittedById;
    private String admittedByName;
    private String message;
}
