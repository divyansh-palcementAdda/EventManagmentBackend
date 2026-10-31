package com.app.eventmanagmentsystem.payload.pass;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BulkActivationConfirmResponse {
    private final String batchReference;
    private final UUID eventId;
    private final String eventName;
    private final int activatedCount;
    private final int emailsQueued;
    private final Instant completedAt;
    private final List<String> activatedPassNumbers;
}

