package com.app.eventmanagmentsystem.payload.pass;

import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PassExportItemResponse {
    private final UUID passId;
    private final String passNumber;
    private final Long sequenceNumber;
    private final com.app.eventmanagmentsystem.model.enums.PassCategory category;
    private final PassStatus status;
    private final UUID eventId;
    private final String eventName;
    private final String eventCode;
    private final String venue;
    private final Instant startsAt;
    private final Instant endsAt;
    private final List<EventDayResponse> validDays;
    private final String attendeeName;
    private final String attendeeEmail;
    private final String attendeeMobileNumber;
    private final String qrData;
}

