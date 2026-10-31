package com.app.eventmanagmentsystem.payload.pass;

import com.app.eventmanagmentsystem.model.enums.PassStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PassResponse {

    private final UUID id;
    private final String passNumber;
    private final Long sequenceNumber;
    private final com.app.eventmanagmentsystem.model.enums.PassCategory category;
    private final PassStatus status;
    private final UUID eventId;
    private final String eventCode;
    private final Instant generatedAt;
    private final Instant activatedAt;
    private final String attendeeName;
    private final String attendeeEmail;
    private final String attendeeMobileNumber;
    private final boolean checkedIn;
    private final Long checkInCount;
    private final Instant lastCheckedInAt;
    private final java.util.List<com.app.eventmanagmentsystem.payload.event.EventDayResponse> validDays;

    /**
     * Raw QR token — returned ONLY at generation time.
     * Null in all other list/get responses.
     */
    private final String rawToken;
}

