package com.app.eventmanagmentsystem.payload.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatsResponse {
    private final long totalEvents;
    private final long activeEvents;
    private final long upcomingEvents;
    private final long totalPassesGenerated;
    private final long totalPassesActivated;
    private final long totalAttendees;
    private final long totalCheckIns;
}

