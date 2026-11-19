package com.app.eventmanagmentsystem.payload.checkin;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CheckInStatsResponse {
    private final UUID eventId;
    private final UUID eventDayId;
    private final long totalCheckedIn;
    private final long goodiesProvided;
    private final long goodiesNotProvided;
}
