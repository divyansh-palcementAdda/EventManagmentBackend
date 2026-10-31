package com.app.eventmanagmentsystem.payload.checkin;

import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CheckInVerificationResponse {
    private final boolean valid;
    private final boolean eligibleForCheckIn;
    private final String status; // READY_FOR_CHECKIN, NOT_ACTIVATED, ALREADY_CHECKED_IN, NOT_VALID_TODAY, REVOKED, INVALID_PASS
    private final String message;
    private final UUID passId;
    private final String passNumber;
    private final UUID eventId;
    private final String eventName;
    private final String eventCode;
    private final String venue;
    private final UUID eventDayId;
    private final Integer dayNumber;
    private final String eventDate;
    private final String eventDayLabel;
    private final UUID attendeeId;
    private final String attendeeName;
    private final String attendeeEmail;
    private final String attendeeOrganization;
    private final String attendeeDesignation;
    private final boolean alreadyCheckedIn;
    private final Instant checkedInAt;
    private final String checkedInByName;
    private final List<EventDayResponse> validDays;
}

