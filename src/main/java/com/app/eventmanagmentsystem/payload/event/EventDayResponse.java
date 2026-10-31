package com.app.eventmanagmentsystem.payload.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class EventDayResponse {
    private final UUID id;
    private final int dayNumber;
    private final LocalDate eventDate;
    private final String label;
}

