package com.app.eventmanagmentsystem.payload.pass;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
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
    private final Map<String, CategoryStats> categoryStats;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStats {
        private long total;
        private long activated;
        private long available;
        private long revoked;
    }
}

