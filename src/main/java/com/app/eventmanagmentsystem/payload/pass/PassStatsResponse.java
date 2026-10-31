package com.app.eventmanagmentsystem.payload.pass;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PassStatsResponse {
    private final UUID eventId;
    private final String eventCode;
    private final String eventName;
    private final long totalPasses;
    private final long activatedPasses;
    private final long availablePasses;
    private final long revokedPasses;
    private final long checkedInCount;
}

