package com.app.eventmanagmentsystem.payload.event;

import com.app.eventmanagmentsystem.model.enums.ActivationPolicy;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class EventResponse {

    private final UUID id;
    private final String name;
    private final String description;
    private final String eventCode;
    private final String passSeriesCode;
    private final String venue;
    private final Instant startsAt;
    private final Instant endsAt;
    private final String timezone;
    private final EventStatus status;
    private final ActivationPolicy activationPolicy;
    private final CheckInPolicy checkInPolicy;
    private final Integer maximumPasses;
    private final long totalPassesGenerated;
    private final java.util.List<EventDayResponse> days;
    private final Instant createdAt;
    private final Instant updatedAt;
}

