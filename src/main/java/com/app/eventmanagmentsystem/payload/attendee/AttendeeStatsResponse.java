package com.app.eventmanagmentsystem.payload.attendee;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeStatsResponse {

    private UUID eventId;
    private long totalAttendees;
    private long confirmedAttendees;
    private long cancelledAttendees;
    private long checkedInCount;
    private long withPass;

    @JsonProperty("checkedIn")
    public long getCheckedIn() {
        return checkedInCount;
    }
}


