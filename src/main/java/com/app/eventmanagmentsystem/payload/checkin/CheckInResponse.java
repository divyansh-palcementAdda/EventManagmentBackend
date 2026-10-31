package com.app.eventmanagmentsystem.payload.checkin;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CheckInResponse {

    private final UUID checkInId;
    private final UUID passId;
    private final String passNumber;
    private final UUID attendeeId;
    private final String attendeeName;
    private final String attendeeEmail;
    private final UUID eventId;
    private final String eventName;
    private final UUID eventDayId;
    private final Integer dayNumber;
    private final String eventDate;
    private final String eventDayLabel;
    private final Instant checkedInAt;
    private final String checkedInByName;
}

